package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.{LambdaMARTConfig, NoopConfig, ShuffleConfig}
import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.{FileLoader, FlinkS3Configuration}
import ai.metarank.mode.inference.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.model.Event
import ai.metarank.rank.LambdaMARTModel.LambdaMARTScorer
import ai.metarank.rank.Model.Scorer
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.rank.ShuffleModel.ShuffleScorer
import ai.metarank.source.{EventSource, RestApiEventSource}
import ai.metarank.util.Logging
import cats.effect.kernel.Ref
import cats.effect.std.Queue
import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.http4s._
import org.http4s.server._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import org.http4s.circe._
import cats.syntax.all._
import fs2.concurrent.SignallingRef
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.values.FeatureStore
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.http4s.blaze.server.BlazeServerBuilder
import io.findify.flinkadt.api._

import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

object Inference extends IOApp with Logging {
  import ai.metarank.mode.TypeInfos._
  override def run(args: List[String]): IO[ExitCode] = {
    args match {
      case configPath :: Nil =>
        for {
          env          <- IO { System.getenv().asScala.toMap }
          confContents <- FileLoader.read(MPath(configPath), env)
          config       <- Config.load(new String(confContents, StandardCharsets.UTF_8))
          mapping      <- IO.pure { FeatureMapping.fromFeatureSchema(config.features, config.models) }
          models       <- loadModels(config, env)
          result <- cluster(config, mapping, models).use {
            _.serve.compile.drain.as(ExitCode.Success).flatTap(_ => IO { logger.info("Metarank API closed") })
          }
        } yield {
          result
        }
      case _ => IO(logger.info("usage: metarank inference <config path>")) *> IO.pure(ExitCode.Error)
    }
  }

  def cluster(config: Config, mapping: FeatureMapping, models: Map[String, Scorer]) = {
    for {
      cluster <- FlinkMinicluster.resource(FlinkS3Configuration(System.getenv()))
      redis   <- RedisEndpoint.create(config.inference.state, config.bootstrap.workdir)
      _       <- Resource.eval(redis.upload)
      source  <- Resource.pure(EventSource.fromConfig(config.inference.source))
      _ <- FeedbackFlow.resource(
        cluster,
        mapping,
        config.inference.state.host,
        config.inference.state.port,
        config.bootstrap.workdir.child("savepoint"),
        config.inference.state.format,
        source.eventStream(_, bounded = false)
      )
      store <- FeatureStoreResource.make(() =>
        RedisStore(RedisConfig(config.inference.state.host, config.inference.state.port, config.inference.state.format))
      )
      storeRef <- Resource.eval(Ref.of[IO, FeatureStoreResource](store))
      mapping = FeatureMapping.fromFeatureSchema(config.features, config.models)
      queue <- Resource.eval(Queue.dropping[IO, Event](1000))
      routes  = HealthApi.routes <+> RankApi(mapping, storeRef, models).routes <+> FeedbackApi(queue).routes
      httpApp = Router("/" -> routes).orNotFound
    } yield {
      BlazeServerBuilder[IO]
        .bindHttp(config.inference.port, config.inference.host)
        .withHttpApp(httpApp)
        .withBanner(Logo.lines)
    }
  }

  def loadModels(config: Config, env: Map[String, String] = Map.empty): IO[Map[String, Scorer]] = {
    config.models.toNel.toList
      .map {
        case (name, LambdaMARTConfig(path, backend, _, _)) =>
          FileLoader.read(path, env).map(file => name -> LambdaMARTScorer(backend, file))
        case (name, ShuffleConfig(maxPositionChange)) =>
          IO.pure(name -> ShuffleScorer(maxPositionChange))
        case (name, _: NoopConfig) =>
          IO.pure(name -> NoopScorer)
      }
      .sequence
      .map(_.toMap)
  }

}
