package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.mode.inference.api.{FeedbackApi, HealthApi, RankApi}
import ai.metarank.mode.inference.ranking.LightGBMScorer
import ai.metarank.source.LocalDirSource.LocalDirWriter
import better.files.File
import cats.effect.kernel.Ref
import cats.effect.{ExitCode, IO, Resource, IOApp}
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
      mapping <- IO.pure { FeatureMapping.fromFeatureSchema(config.features, config.interactions) }
      result  <- cluster(dir, config, mapping, cmd).use { _.serve.compile.drain.as(ExitCode.Success) }
    } yield result
  }

  def cluster(dir: File, config: Config, mapping: FeatureMapping, cmd: InferenceCmdline) = {
    for {
      cluster <- FlinkMinicluster.resource()
      redis   <- RedisEndpoint.create(cmd.embeddedRedis, cmd.redisHost, cmd.redisPort)
      _       <- Resource.eval(redis.upload)
      _       <- FeedbackFlow.resource(cluster, dir.toString(), mapping, cmd, redis.host)
      s       <- server(cmd, config, dir, redis.host)
    } yield s
  }

  def server(cmd: InferenceCmdline, config: Config, dir: File, redisHost: String) = {
    val store   = RedisStore(RedisConfig(redisHost, cmd.redisPort, cmd.format))
    val mapping = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
    val scorer  = LightGBMScorer(cmd.model.contentAsString)
    for {
      writer <- LocalDirWriter.create(dir)
      routes  = HealthApi.routes <+> RankApi(mapping, store, scorer).routes <+> FeedbackApi(writer).routes
      httpApp = Router("/" -> routes).orNotFound
    } yield BlazeServerBuilder[IO]
      .bindHttp(cmd.port, cmd.host)
      .withHttpApp(httpApp)
  }
}
