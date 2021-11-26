package ai.metarank.mode.train

import ai.metarank.config.Config
import ai.metarank.feature.FeatureMapping
import ai.metarank.flow.ImpressionInjectFunction
import ai.metarank.model.Event
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.source.{EventSource, FileEventSource}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.flink.util.Compress
import io.findify.featury.flink.{FeatureProcessFunction, Featury}
import io.findify.featury.model.Schema
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import io.findify.flinkadt.api._
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.scala.extensions.acceptPartialFunctions

import scala.language.higherKinds
import scala.concurrent.duration._

object Train extends IOApp with Logging {
  import ai.metarank.flow.DataStreamOps._

  override def run(args: List[String]): IO[ExitCode] = for {
    cmd    <- TrainCmdline.parse(args)
    config <- Config.load(cmd.config)
    _      <- run(config, cmd)
  } yield {
    ExitCode.Success
  }

  def run(config: Config, cmd: TrainCmdline) = IO {
    val mapping       = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
    val featurySchema = Schema(mapping.features.flatMap(_.states))
    val streamEnv     = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(1)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.BATCH)
    val raw: DataStream[Event] = FileEventSource(cmd.eventPath).eventStream(streamEnv)
    val impressions = raw
      .collect { case f: FeedbackEvent => f }
      .keyingBy {
        case int: InteractionEvent => int.ranking
        case rank: RankingEvent    => rank.id
      }
      .process(new ImpressionInjectFunction("impression", 30.minutes))
    val events  = raw.union(impressions)
    val writes  = events.flatMap(e => mapping.features.flatMap(_.writes(e))).id("expand-writes")
    val updates = Featury.process(writes, featurySchema, 20.seconds).id("process-writes")
    val state   = updates.getSideOutput(FeatureProcessFunction.stateTag)
    Featury.writeState(state, new Path(cmd.outDir + "/state"), Compress.ZstdCompression(3))
    streamEnv.execute()
  }
}
