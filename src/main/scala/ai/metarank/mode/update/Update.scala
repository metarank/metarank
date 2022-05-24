package ai.metarank.mode.update

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.{CliApp, FileLoader, FlinkS3Configuration}
import ai.metarank.mode.standalone.{FeatureStoreResource, FeedbackFlow, FlinkMinicluster, Logo, RedisEndpoint}
import ai.metarank.source.EventSource
import ai.metarank.util.Logging
import cats.effect.unsafe.implicits.global
import io.findify.flink.api.StreamExecutionEnvironment

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import io.findify.flinkadt.api._

import java.nio.charset.StandardCharsets

/** Supposed to be run from the flink k8s operator, so no IO stuff here.
  */

object Update extends Logging {
  import ai.metarank.mode.TypeInfos._

  def main(args: Array[String]): Unit = {
    args.toList match {
      case confPath :: Nil =>
        val env          = System.getenv().asScala.toMap
        val confContents = FileLoader.read(MPath(confPath), env).unsafeRunSync() // YOLO
        val config       = Config.load(new String(confContents, StandardCharsets.UTF_8)).unsafeRunSync()
        val mapping      = FeatureMapping.fromFeatureSchema(config.features, config.models)
        val source       = EventSource.fromConfig(config.inference.source)
        val streamEnv    = StreamExecutionEnvironment.getExecutionEnvironment
        streamEnv.setParallelism(config.bootstrap.parallelism)
        FeedbackFlow.job(
          env = streamEnv,
          mapping = mapping,
          redisHost = config.inference.state.host,
          redisPort = config.inference.state.port,
          format = config.inference.state.format,
          impress = config.bootstrap.syntheticImpression,
          events = source.eventStream(_, bounded = false),
          batchPeriod = 100.millis
        )
        streamEnv.execute("metarank-update")
      case _ => logger.error("usage: metarank update <config path>")
    }
  }
}
