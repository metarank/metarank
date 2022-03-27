package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.mode.{FileLoader, FlinkS3Configuration}
import ai.metarank.mode.inference.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.mode.inference.ranking.LightGBMScorer
import ai.metarank.source.LocalDirSource.LocalDirWriter
import ai.metarank.util.Logging
import better.files.File
import cats.effect.kernel.Ref
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

import scala.collection.JavaConverters._

object Inference extends IOApp with Logging {
  import ai.metarank.mode.TypeInfos._
  override def run(args: List[String]): IO[ExitCode] = {
    val dir = File.newTemporaryDirectory("events_queue_").deleteOnExit()
    for {
      env          <- IO { System.getenv().asScala.toMap }
      cmd          <- InferenceCmdline.parse(args, env)
      confContents <- FileLoader.loadLocal(cmd.config, env).map(new String(_))
      config       <- Config.load(confContents)
      mapping      <- IO.pure { FeatureMapping.fromFeatureSchema(config.features, config.interactions) }
      model        <- FileLoader.loadLocal(cmd.model, env).map(new String(_))
      result <- cluster(dir, config, mapping, cmd, model).use {
        _.serve.compile.drain.as(ExitCode.Success).flatTap(_ => IO { logger.info("Metarank API closed") })
      }
    } yield result
  }

  def cluster(dir: File, config: Config, mapping: FeatureMapping, cmd: InferenceCmdline, model: String) = {
    for {
      cluster  <- FlinkMinicluster.resource(FlinkS3Configuration(System.getenv()))
      redis    <- RedisEndpoint.create(cmd.embeddedRedis, cmd.redisHost, cmd.redisPort)
      _        <- Resource.eval(redis.upload)
      _        <- FeedbackFlow.resource(cluster, dir.toString(), mapping, cmd, redis.host)
      store    <- FeatureStoreResource.make(() => RedisStore(RedisConfig(redis.host, cmd.redisPort, cmd.format)))
      storeRef <- Resource.eval(Ref.of[IO, FeatureStoreResource](store))
      mapping = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
      scorer  = LightGBMScorer(model)
      writer <- LocalDirWriter.create(dir)
      routes  = HealthApi.routes <+> RankApi(mapping, storeRef, scorer).routes <+> FeedbackApi(writer).routes
      httpApp = Router("/" -> routes).orNotFound
    } yield {
      BlazeServerBuilder[IO]
        .bindHttp(cmd.port, cmd.host)
        .withHttpApp(httpApp)
    }
  }
}
