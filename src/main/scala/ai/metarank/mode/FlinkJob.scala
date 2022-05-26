package ai.metarank.mode

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.util.Logging
import cats.effect.unsafe.implicits.global
import io.findify.flink.api.StreamExecutionEnvironment

import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets

trait FlinkJob extends Logging {
  def main(args: Array[String]): Unit = {
    args.toList match {
      case confPath :: Nil =>
        val env          = System.getenv().asScala.toMap
        val confContents = FileLoader.read(MPath(confPath), env).unsafeRunSync() // YOLO
        val config       = Config.load(new String(confContents, StandardCharsets.UTF_8)).unsafeRunSync()
        val mapping      = FeatureMapping.fromFeatureSchema(config.features, config.models)
        val streamEnv    = StreamExecutionEnvironment.getExecutionEnvironment
        config.bootstrap.parallelism.foreach(streamEnv.setParallelism)
        job(config, mapping, streamEnv)
      case _ => logger.error(usage)
    }

  }
  def usage: String
  def job(config: Config, mapping: FeatureMapping, streamEnv: StreamExecutionEnvironment): Unit
}
