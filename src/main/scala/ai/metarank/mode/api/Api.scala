package ai.metarank.mode.api

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.ModelConfig.{LambdaMARTConfig, NoopConfig, ShuffleConfig}
import ai.metarank.mode.standalone.{FeatureStoreResource, Logo}
import ai.metarank.mode.standalone.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.mode.CliApp
import ai.metarank.model.Event
import ai.metarank.rank.LambdaMARTModel.LambdaMARTScorer
import ai.metarank.rank.Model.Scorer
import ai.metarank.rank.NoopModel.NoopScorer
import ai.metarank.rank.ShuffleModel.ShuffleScorer
import ai.metarank.util.fs.FS
import cats.effect.kernel.Ref
import cats.effect.std.Queue
import cats.effect.{ExitCode, IO, Resource}
import cats.syntax.all._
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router

object Api extends CliApp {
  override def run(
      args: List[String],
      env: Map[String, String],
      config: Config,
      mapping: FeatureMapping
  ): IO[ExitCode] = {
    httpResource(config, env).use(
      _.serve.compile.drain.as(ExitCode.Success).flatTap(_ => IO { logger.info("Metarank API closed") })
    )
  }

  def httpResource(config: Config, env: Map[String, String]) = for {
    models <- Resource.eval(loadModels(config, env))
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

  override def usage: String = "usage: metarank inference <config path>"

  def loadModels(config: Config, env: Map[String, String] = Map.empty): IO[Map[String, Scorer]] = {
    config.models.toNel.toList
      .map {
        case (name, LambdaMARTConfig(path, backend, _, _)) =>
          FS.read(path, env).map(file => name -> LambdaMARTScorer(backend, file))
        case (name, ShuffleConfig(maxPositionChange)) =>
          IO.pure(name -> ShuffleScorer(maxPositionChange))
        case (name, _: NoopConfig) =>
          IO.pure(name -> NoopScorer)
      }
      .sequence
      .map(_.toMap)
  }

}
