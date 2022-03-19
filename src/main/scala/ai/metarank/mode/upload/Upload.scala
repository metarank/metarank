package ai.metarank.mode.upload

import ai.metarank.mode.inference.FlinkMinicluster
import ai.metarank.mode.{AsyncFlinkJob, FlinkS3Configuration}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp, Resource}
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.Featury
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.flink.util.Compress
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import io.findify.flinkadt.api._
import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.language.higherKinds

object Upload extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    cmd <- UploadCmdline.parse(args, System.getenv().asScala.toMap)
    _   <- run(cmd).use(_ => IO.unit)
    _   <- IO { logger.info("Upload done") }
  } yield {
    ExitCode.Success
  }

  def run(cmd: UploadCmdline) = upload(cmd.dir, cmd.host, cmd.port, cmd.format, cmd.batchSize)

  def upload(dir: String, host: String, port: Int, format: StoreCodec, batchSize: Int) =
    for {
      cluster <- FlinkMinicluster.resource(FlinkS3Configuration(System.getenv()))
      job <- AsyncFlinkJob.execute(cluster) { env =>
        {
          val features = env.fromSource(
            Featury.readFeatures(new Path(dir), Compress.NoCompression),
            WatermarkStrategy.noWatermarks(),
            "read"
          )
          features.addSink(FeatureStoreSink(RedisStore(RedisConfig(host, port, format)), batchSize))
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
