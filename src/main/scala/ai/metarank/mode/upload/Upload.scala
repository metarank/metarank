package ai.metarank.mode.upload

import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.Featury
import io.findify.featury.flink.format.FeatureStoreSink
import io.findify.featury.flink.util.Compress
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import io.findify.flinkadt.api._
import org.apache.flink.api.common.eventtime.WatermarkStrategy

import scala.language.higherKinds

object Upload extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = for {
    cmd <- UploadCmdline.parse(args)
    _   <- run(cmd)
  } yield {
    ExitCode.Success
  }

  def run(cmd: UploadCmdline) = IO {
    upload(cmd.dir, cmd.host, cmd.port, cmd.format, cmd.batchSize)
  }

  def upload(dir: String, host: String, port: Int, format: StoreCodec, batchSize: Int) = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val features = env.fromSource(
      Featury.readFeatures(new Path(dir), Compress.NoCompression),
      WatermarkStrategy.noWatermarks(),
      "read"
    )
    features.addSink(FeatureStoreSink(RedisStore(RedisConfig(host, port, format)), batchSize))
    env.execute("upload")
  }
}
