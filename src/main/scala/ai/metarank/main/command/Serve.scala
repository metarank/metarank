package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.api.routes
import ai.metarank.api.routes.{FeedbackApi, HealthApi, RankApi, TrainApi}
import ai.metarank.config.{ApiConfig, Config, InputConfig}
import ai.metarank.flow.{ClickthroughJoinBuffer, MetarankFlow}
import ai.metarank.fstore.{ClickthroughStore, Persistence}
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
      ctsResource: Resource[IO, ClickthroughStore],
      mapping: FeatureMapping,
      args: ServeArgs
  ): IO[Unit] = {
    storeResource.use(store => {
      ctsResource.use(cts => {
        val buffer = ClickthroughJoinBuffer(conf.core.clickthrough, store.values, cts, mapping)
        conf.input match {
          case None =>
            info("no stream input defined in config, using only REST API") *> api(store,cts,  mapping, conf.api, buffer)
          case Some(sourceConfig) =>
            val source = EventSource.fromConfig(sourceConfig)
            MetarankFlow
              .process(store, source.stream, mapping, buffer)
              .background
              .use(_ => info(s"started ${source.conf} source") *> api(store, cts, mapping, conf.api, buffer))
        }
      })
    })
  }

  def api(store: Persistence, cts: ClickthroughStore, mapping: FeatureMapping, conf: ApiConfig, buffer: ClickthroughJoinBuffer) = {
    val health   = HealthApi(store).routes
    val rank     = RankApi(Ranker(mapping, store)).routes
    val feedback = FeedbackApi(store, mapping, buffer).routes
    val train    = TrainApi(mapping, store, cts).routes
    val routes   = health <+> rank <+> feedback <+> train
    val httpApp  = Router("/" -> routes).orNotFound
    val api = BlazeServerBuilder[IO]
      .bindHttp(conf.port.value, conf.host.value)
      .withHttpApp(httpApp)
      .withBanner(Logo.lines)

    info("Starting API...") *> api.serve.compile.drain
  }
}
