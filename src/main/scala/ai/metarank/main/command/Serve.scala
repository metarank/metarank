package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.{ApiConfig, Config}
import ai.metarank.flow.{ClickthroughImpressionFlow, FeatureValueFlow, FeatureValueSink}
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.ServeArgs
import ai.metarank.main.Logo
import ai.metarank.main.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.model.Event
import ai.metarank.source.ModelCache
import ai.metarank.source.ModelCache.MemoryModelCache
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.implicits._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router

object Serve {
  def run(
      conf: Config,
      storeResource: Resource[IO, Persistence],
      mapping: FeatureMapping,
      args: ServeArgs
  ): IO[Unit] = {
    val flowResource = for {
      store <- storeResource
      queue <- Resource.liftK(Queue.bounded[IO, Option[Event]](1024))
      _     <- api(store, queue, mapping, conf.api).background
    } yield {
      (store, queue)
    }
    flowResource.use { case (store, queue) => serve(store, queue, mapping) }
  }

  def serve(store: Persistence, queue: Queue[IO, Option[Event]], mapping: FeatureMapping): IO[Unit] = {
    val ct    = ClickthroughImpressionFlow(store, mapping)
    val event = FeatureValueFlow(mapping, store)
    val sink  = FeatureValueSink(store)
    fs2.Stream
      .fromQueueNoneTerminated(queue)
      .through(ct.process)
      .through(event.process)
      .through(sink.write)
      .compile
      .drain
  }

  def api(store: Persistence, queue: Queue[IO, Option[Event]], mapping: FeatureMapping, conf: ApiConfig) = {
    val health   = HealthApi(store).routes
    val rank     = RankApi(mapping, store, MemoryModelCache(store)).routes
    val feedback = FeedbackApi(queue).routes
    val routes   = health <+> rank <+> feedback
    val httpApp  = Router("/" -> routes).orNotFound
    val api = BlazeServerBuilder[IO]
      .bindHttp(conf.port.value, conf.host.value)
      .withHttpApp(httpApp)
      .withBanner(Logo.lines)

    api.serve.compile.drain
  }
}
