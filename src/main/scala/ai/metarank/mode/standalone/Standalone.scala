package ai.metarank.mode.standalone

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.{LambdaMARTConfig, NoopConfig, ShuffleConfig}
import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.api.Api
import ai.metarank.mode.{CliApp, FlinkS3Configuration}
import ai.metarank.rank.Model.Scorer
import ai.metarank.source.EventSource
import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.http4s._
import org.http4s.server._
import org.http4s.dsl.io._
import io.circe.syntax._
import cats.syntax.all._
import io.findify.flinkadt.api._
import org.apache.flink.api.common.JobID

import scala.concurrent.duration._

object Standalone extends CliApp {
  import ai.metarank.mode.TypeInfos._

  override def usage: String = "usage: standalone <config path>"

  override def run(
      args: List[String],
      env: Map[String, String],
      config: Config,
      mapping: FeatureMapping
  ): IO[ExitCode] = {
    for {
      models <- Api.loadModels(config, env)
      exit   <- startJobApi(env, config, mapping, models)
    } yield {
      exit
    }
  }

  def startJobApi(
      env: Map[String, String],
      config: Config,
      mapping: FeatureMapping,
      models: Map[String, Scorer]
  ): IO[ExitCode] = Api
    .httpResource(config, env)
    .use(http =>
      cluster(config, mapping, models).use(_ =>
        http.serve.compile.drain.as(ExitCode.Success).flatTap(_ => IO { logger.info("Metarank API closed") })
      )
    )

  def cluster(config: Config, mapping: FeatureMapping, models: Map[String, Scorer]): Resource[IO, JobID] = ???
//  {
//    for {
//      cluster <- FlinkMinicluster.resource(FlinkS3Configuration(System.getenv()))
//      redis   <- RedisEndpoint.create(config.state)
//      _       <- Resource.eval(redis.upload)
//      source  <- Resource.pure(EventSource.fromConfig(config.input))
//      job <- FeedbackFlow.resource(
//        cluster,
//        mapping,
//        config.inference.state.host,
//        config.inference.state.port,
//        config.bootstrap.workdir.child("savepoint"),
//        config.inference.state.format,
//        config.bootstrap.syntheticImpression,
//        source.eventStream(_, bounded = false),
//        batchPeriod = 100.millis
//      )
//    } yield {
//      job
//    }
//  }

}
