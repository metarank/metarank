package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelKey
import ai.metarank.main.Logo
import ai.metarank.model.Event
import ai.metarank.rank.Model.Scorer
import ai.metarank.source.ModelCache
import cats.effect.std.Queue
import cats.effect.{IO, Ref, Resource}
import org.http4s.blaze.server.BlazeServerBuilder
import cats.implicits._
import org.http4s.server.Router

object ApiResource {
  def create(
      config: Config,
      store: Persistence,
      models: ModelCache,
      queue: Queue[IO, Option[Event]]
  ): Resource[IO, BlazeServerBuilder[IO]] = for {
    mappings <- Resource.pure(config.env.map(FeatureMapping.fromEnvConfig))
    routes  = HealthApi(store).routes <+> RankApi(mappings, store, models).routes <+> FeedbackApi(queue).routes
    httpApp = Router("/" -> routes).orNotFound
  } yield {
    BlazeServerBuilder[IO]
      .bindHttp(config.api.port.value, config.api.host.value)
      .withHttpApp(httpApp)
      .withBanner(Logo.lines)
  }
}
