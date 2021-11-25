package ai.metarank.mode.train

import ai.metarank.config.Config
import ai.metarank.feature.FeatureMapping
import ai.metarank.model.Event
import ai.metarank.source.{EventSource, FileEventSource}
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.flink.Featury
import io.findify.featury.model.Schema
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.api.scala._

import scala.concurrent.duration._

object Train extends IOApp {
  import ai.metarank.util.DataStreamOps._

  override def run(args: List[String]): IO[ExitCode] = for {
    cmd    <- TrainCmdline.parse(args)
    config <- Config.load(cmd.config)
    _      <- run(config, cmd)
  } yield {
    ExitCode.Success
  }

  def run(config: Config, cmd: TrainCmdline) = IO {
    val mapping       = FeatureMapping.fromFeatureSchema(config.feature)
    val featurySchema = Schema(mapping.features.flatMap(_.states))
    val streamEnv     = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(1)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val events: DataStream[Event] = FileEventSource(cmd.eventPath).eventStream(streamEnv)
    val writes                    = events.flatMap(e => mapping.features.flatMap(_.writes(e))).id("expand-writes")
    val updates                   = Featury.process(writes, featurySchema, 20.seconds).id("process-writes")
  }
}
