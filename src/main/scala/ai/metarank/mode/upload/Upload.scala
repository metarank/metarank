package ai.metarank.mode.upload

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.mode.standalone.FlinkMinicluster
import ai.metarank.mode.{AsyncFlinkJob, CliApp, FlinkS3Configuration}
import ai.metarank.source.FeatureValueWatermarkStrategy
import cats.effect.{ExitCode, IO, Resource}
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.Featury
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.flink.util.Compress
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import io.findify.flinkadt.api._
import org.apache.flink.api.common.{JobID, JobStatus, RuntimeExecutionMode}

import scala.concurrent.duration._

object Upload extends CliApp {
  import ai.metarank.flow.DataStreamOps._
  import ai.metarank.mode.TypeInfos._

  override def usage: String = "usage: metarank upload <config path>"

  override def run(
      args: List[String],
      env: Map[String, String],
      config: Config,
      mapping: FeatureMapping
  ): IO[ExitCode] = for {
    _ <- upload(
      config.bootstrap.workdir.child("features"),
      config.inference.state.host,
      config.inference.state.port,
      config.inference.state.format,
      buffer = 1.second
    ).use { _ => IO { logger.info("Upload done") } }
  } yield {
    ExitCode.Success
  }

  def upload(dir: MPath, host: String, port: Int, format: StoreCodec, buffer: FiniteDuration) =
    for {
      cluster <- FlinkMinicluster.resource(FlinkS3Configuration(System.getenv()))
      job <- AsyncFlinkJob.execute(cluster, name = Some("metarank-upload")) { env =>
        {
          val features = env
            .fromSource(
              Featury.readFeatures(dir.flinkPath, Compress.NoCompression),
              FeatureValueWatermarkStrategy(),
              "read"
            )
            .keyBy(_.key.tenant)
            .process(WindowBatchFunction(buffer, 1024))
            .id("make-batches")

          features.sinkTo(FeatureStoreSink(RedisStore(RedisConfig(host, port, format))))
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
