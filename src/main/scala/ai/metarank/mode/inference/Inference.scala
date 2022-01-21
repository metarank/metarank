package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.mode.inference.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.mode.inference.ranking.LightGBMScorer
import ai.metarank.source.LocalDirSource.LocalDirWriter
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s._
import org.http4s.server._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import org.http4s.circe._
import cats.syntax.all._
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.http4s.blaze.server.BlazeServerBuilder
import io.findify.flinkadt.api._

object Inference extends IOApp {
  import ai.metarank.mode.TypeInfos._
  override def run(args: List[String]): IO[ExitCode] = {
    val dir = File.newTemporaryDirectory("events_queue_").deleteOnExit()
    for {
      cmd     <- InferenceCmdline.parse(args)
      config  <- Config.load(cmd.config)
      mapping <- IO { FeatureMapping.fromFeatureSchema(config.features, config.interactions) }
      result <- FlinkMinicluster
        .resource()
        .use(cluster => {
          FeedbackFlow
            .resource(cluster, dir.toString(), mapping, cmd)
            .use(_ => {
              serve(cmd, config, dir)
            })
        })
    } yield {
      result
    }
  }

  def serve(cmd: InferenceCmdline, config: Config, dir: File) = {
    val store   = RedisStore(RedisConfig(cmd.redisHost, cmd.redisPort, cmd.format))
    val mapping = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
    val scorer  = LightGBMScorer(cmd.model.contentAsString)
    val routes =
      HealthApi.routes <+> RankApi(mapping, store, scorer).routes <+> FeedbackApi(new LocalDirWriter(dir)).routes
    val httpApp = Router("/" -> routes).orNotFound
    BlazeServerBuilder[IO].bindHttp(cmd.port, cmd.host).withHttpApp(httpApp).serve.compile.drain.as(ExitCode.Success)
  }
}
