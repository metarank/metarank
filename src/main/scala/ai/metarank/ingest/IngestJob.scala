package ai.metarank.ingest

import ai.metarank.config.IngestConfig.{APIIngestConfig, FileIngestConfig}
import ai.metarank.config.{Config, IngestConfig}
import ai.metarank.ingest.source.FileEventSource
import cats.effect.IO
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.core.execution.JobClient
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.api.scala._

case class IngestJob(config: Config) {

  def run(): IO[JobClient] = IO {
    val streamEnv = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(1)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.AUTOMATIC)
    val source = config.ingest match {
      case file: FileIngestConfig => FileEventSource(file).source(streamEnv)
      case api: APIIngestConfig   => ???
    }
    streamEnv.executeAsync("test")
  }
}
