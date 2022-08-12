package ai.metarank.main.command

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
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
      queue <- Resource.liftK(Queue.dropping[IO, Option[Event]](1024))
      routes = HealthApi(store).routes <+> RankApi(mapping, store, MemoryModelCache(store)).routes <+> FeedbackApi(
        queue
      ).routes
      httpApp = Router("/" -> routes).orNotFound
      api = BlazeServerBuilder[IO]
        .bindHttp(conf.api.port.value, conf.api.host.value)
        .withHttpApp(httpApp)
        .withBanner(Logo.lines)

      _ <- api.serve.compile.drain.background
      ct    = ClickthroughImpressionFlow(store, mapping)
      event = FeatureValueFlow(mapping, store)
      sink  = FeatureValueSink(store)
      flow  = fs2.Stream.fromQueueNoneTerminated(queue).through(ct.process).through(event.process).through(sink.write)
    } yield {
      flow
    }
    flowResource.use(_.compile.drain)
  }
}
