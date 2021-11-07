package ai.metarank.ingest

import ai.metarank.config.IngestConfig.{APIIngestConfig, FileIngestConfig}
import ai.metarank.config.{Config, IngestConfig}
import ai.metarank.ingest.source.{FileEventSource, HttpEventSource}
import cats.effect.{ExitCode, IO, IOApp}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.core.execution.JobClient
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.scala._

object IngestJob extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    runJob(APIIngestConfig(8080)).map(_ => ExitCode.Success)
  }

  def runJob(config: IngestConfig) = IO {
    val streamEnv = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(1)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.AUTOMATIC)
    val source = config match {
      case file: FileIngestConfig => FileEventSource(file).source(streamEnv)
      case api: APIIngestConfig   => HttpEventSource(api).source(streamEnv)
    }
    val x  = source.executeAndCollect(1000)
    val br = 1
  }
}
