package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.api.routes
import ai.metarank.api.routes.{FeedbackApi, HealthApi, RankApi, TrainApi}
import ai.metarank.config.{ApiConfig, Config}
import ai.metarank.flow.{ClickthroughImpressionFlow, FeatureValueFlow, FeatureValueSink}
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ServeArgs
import ai.metarank.main.Logo
import ai.metarank.model.Event
import ai.metarank.rank.Ranker
import ai.metarank.source.ModelCache
import ai.metarank.source.ModelCache.MemoryModelCache
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
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
    storeResource.use(store => api(store, mapping, conf.api))
  }

  def api(store: Persistence, mapping: FeatureMapping, conf: ApiConfig) = {
    val health   = HealthApi(store).routes
    val cache    = MemoryModelCache(store)
    val rank     = RankApi(Ranker(mapping, store)).routes
    val feedback = FeedbackApi(store, mapping).routes
    val train    = TrainApi(mapping, store, cache).routes
    val routes   = health <+> rank <+> feedback <+> train
    val httpApp  = Router("/" -> routes).orNotFound
    val api = BlazeServerBuilder[IO]
      .bindHttp(conf.port.value, conf.host.value)
      .withHttpApp(httpApp)
      .withBanner(Logo.lines)

    api.serve.compile.drain.flatTap(_ => inf)
  }
}
