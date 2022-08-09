package ai.metarank.main.api

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.fstore.Persistence
import ai.metarank.main.Logo
import ai.metarank.model.Event
import ai.metarank.rank.Model.Scorer
import cats.effect.std.Queue
import cats.effect.{ExitCode, IO, Resource}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import cats.implicits._

object Api {
  case class ApiResources(http: BlazeServerBuilder[IO], persistence: Persistence)
//  override def run(
//      args: List[String],
//      env: Map[String, String],
//      config: Config,
//      mapping: FeatureMapping
//  ): IO[ExitCode] = {
//    httpResource(config, env).use(api => {
//      List(api.http.serve.compile.drain, api.persistence.run()).parSequence_
//        .map(_ => ExitCode.Success)
//        .flatTap(_ => info("Metarank API closed"))
//
//    })
//  }

  def httpResource(config: Config): Resource[IO, ApiResources] = for {
    models <- Resource.eval(loadModels(config))
    mappings = config.env.map(FeatureMapping.fromEnvConfig)
    store <- Persistence.fromConfig(mappings.map(_.schema), config.state)
    queue <- Resource.eval(Queue.dropping[IO, Option[Event]](1000))
    routes  = HealthApi(store).routes <+> RankApi(mappings, store, models).routes <+> FeedbackApi(queue).routes
    httpApp = Router("/" -> routes).orNotFound
  } yield {
    val builder = BlazeServerBuilder[IO]
      .bindHttp(config.api.port.value, config.api.host.value)
      .withHttpApp(httpApp)
      .withBanner(Logo.lines)
    ApiResources(builder, store)
  }

  // override def usage: String = "usage: metarank api <config path>"

  def loadModels(config: Config): IO[Map[String, Scorer]] = ???
//  {
//    config.models.toNel.toList
//      .map {
//        case (name, LambdaMARTConfig(backend, _, _)) =>
//          FS.read(path, env)
//            .flatTap(bytes => IO { logger.info(s"loaded model $name file, size=${bytes.length}b") })
//            .map(file => name -> LambdaMARTScorer(backend, file))
//            .onError(err => IO { logger.error(s"cannot load ranking model: ${err.getMessage}", err) })
//        case (name, ShuffleConfig(maxPositionChange)) =>
//          IO.pure(name -> ShuffleScorer(maxPositionChange))
//        case (name, _: NoopConfig) =>
//          IO.pure(name -> NoopScorer)
//      }
//      .sequence
//      .flatTap(models => IO { logger.info(s"initialized scorers for models ${models.map(_._1)}") })
//      .map(_.toMap)
//  }

}
