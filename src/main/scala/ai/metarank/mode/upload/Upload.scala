package ai.metarank.mode.upload

import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.inference.FlinkMinicluster
import ai.metarank.mode.{AsyncFlinkJob, FileLoader, FlinkS3Configuration}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp, Resource}
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.Featury
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.flink.util.Compress
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import io.findify.flinkadt.api._
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.api.common.eventtime.WatermarkStrategy

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object Upload extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = args match {
    case configPath :: Nil =>
      for {
        env          <- IO { System.getenv().asScala.toMap }
        confContents <- FileLoader.read(MPath(configPath), env)
        config       <- Config.load(new String(confContents, StandardCharsets.UTF_8))
        _ <- upload(
          config.bootstrap.workdir.child("features"),
          config.inference.state.host,
          config.inference.state.port,
          config.inference.state.format
        ).use { _ => IO { logger.info("Upload done") } }
      } yield {
        ExitCode.Success
      }
    case _ =>
      IO { logger.error("usage: metarank upload <config path>") } *> IO.pure(ExitCode.Success)
  }

  def upload(dir: MPath, host: String, port: Int, format: StoreCodec) =
    for {
      cluster <- FlinkMinicluster.resource(FlinkS3Configuration(System.getenv()))
      job <- AsyncFlinkJob.execute(cluster) { env =>
        {
          val features = env.fromSource(
            Featury.readFeatures(dir.flinkPath, Compress.NoCompression),
            WatermarkStrategy.noWatermarks(),
            "read"
          )
          features.addSink(FeatureStoreSink(RedisStore(RedisConfig(host, port, format)), 1024))
        }
      }
      _ <- Resource.eval(blockUntilFinished(cluster, job))
    } yield {}

  def blockUntilFinished(cluster: FlinkMinicluster, job: JobID): IO[Unit] = for {
    _ <- IO.sleep(1.second)
    _ <- IO.fromCompletableFuture(IO { cluster.client.getJobStatus(job) }).flatMap {
      case JobStatus.FINISHED => IO { logger.info(s"job $job is finished") }
      case other =>
        IO { logger.warn(s"upload job not yet finished (status=$other), waiting 1s more") } *> blockUntilFinished(
          cluster,
          job
        )
    }
  } yield {}

}
