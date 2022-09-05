package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.api.routes
import ai.metarank.api.routes.{FeedbackApi, HealthApi, RankApi, TrainApi}
import ai.metarank.config.{ApiConfig, Config, InputConfig}
import ai.metarank.flow.MetarankFlow
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ServeArgs
import ai.metarank.main.Logo
import ai.metarank.rank.Ranker
import ai.metarank.source.EventSource
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router

object Serve extends Logging {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: ServeArgs
  ): IO[Unit] = {
    storeResource.use(store => {
      conf.input match {
        case None =>
          info("no stream input defined in config, using only REST API") *> api(store, mapping, conf.api)
        case Some(sourceConfig) =>
          val source = EventSource.fromConfig(sourceConfig)
          MetarankFlow
            .process(store, source.stream, mapping)
            .background
            .use(_ => info(s"started ${source.conf} source") *> api(store, mapping, conf.api))
      }

    })
  }

  def api(store: Persistence, mapping: FeatureMapping, conf: ApiConfig) = {
    val health   = HealthApi(store).routes
    val rank     = RankApi(Ranker(mapping, store)).routes
    val feedback = FeedbackApi(store, mapping).routes
    val train    = TrainApi(mapping, store).routes
    val routes   = health <+> rank <+> feedback <+> train
    val httpApp  = Router("/" -> routes).orNotFound
    val api = BlazeServerBuilder[IO]
      .bindHttp(conf.port.value, conf.host.value)
      .withHttpApp(httpApp)

    info("Starting API...") *> api.serve.compile.drain
  }
}
