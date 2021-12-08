package ai.metarank.mode.inference

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.mode.inference.api.{HealthApi, RankApi}
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s._
import org.http4s.server._
import org.http4s.dsl.io._
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.syntax._
import org.http4s.circe._
import cats.implicits._
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.http4s.blaze.server.BlazeServerBuilder

object Inference extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = for {
    cmd    <- InferenceCmdline.parse(args)
    config <- Config.load(cmd.config)
    result <- serve(cmd, config)
  } yield {
    result
  }

  def serve(cmd: InferenceCmdline, config: Config) = {
    val store   = RedisStore(RedisConfig(cmd.redisHost, cmd.redisPort, cmd.format))
    val mapping = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
    val routes  = HealthApi.routes <+> RankApi(mapping, store, cmd.model.contentAsString).routes
    val httpApp = Router("/" -> routes).orNotFound
    BlazeServerBuilder[IO].bindHttp(cmd.port).withHttpApp(httpApp).serve.compile.drain.as(ExitCode.Success)
  }
}
