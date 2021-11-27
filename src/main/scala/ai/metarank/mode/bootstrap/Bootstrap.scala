package ai.metarank.mode.bootstrap

import ai.metarank.config.Config
import ai.metarank.feature.FeatureMapping
import ai.metarank.flow.{ClickthroughJoinFunction, DatasetSink, ImpressionInjectFunction}
import ai.metarank.model.Clickthrough.CTJoin
import ai.metarank.model.{Clickthrough, Event}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.source.{EventSource, FileEventSource}
import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.flink.util.Compress
import io.findify.featury.flink.{FeatureBootstrapFunction, FeatureProcessFunction, Featury}
import io.findify.featury.model.{Key, Schema, State}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import io.findify.flinkadt.api._
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend
import org.apache.flink.core.fs.Path
import org.apache.flink.state.api.{OperatorTransformation, Savepoint}
import org.apache.flink.streaming.api.scala.extensions.acceptPartialFunctions

import scala.language.higherKinds
import scala.concurrent.duration._

object Bootstrap extends IOApp with Logging {
  import ai.metarank.flow.DataStreamOps._
  import org.apache.flink.DataSetOps._

  case object StateKeySelector extends KeySelector[State, Key] { override def getKey(value: State): Key = value.key }

  override def run(args: List[String]): IO[ExitCode] = for {
    cmd    <- BootstrapCmdline.parse(args)
    config <- Config.load(cmd.config)
    _      <- run(config, cmd)
  } yield {
    ExitCode.Success
  }

  def run(config: Config, cmd: BootstrapCmdline) = IO {
    File(cmd.outDir).createDirectoryIfNotExists(createParents = true)
    val mapping       = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
    val featurySchema = Schema(mapping.features.flatMap(_.states))
    val streamEnv     = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(cmd.parallelism)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.BATCH)

    logger.info("starting historical data processing")
    val raw: DataStream[Event] = FileEventSource(cmd.eventPath).eventStream(streamEnv).id("load")
    val groupedFeedback = raw
      .collect { case f: FeedbackEvent => f }
      .id("select-feedback")
      .keyingBy {
        case int: InteractionEvent => int.ranking
        case rank: RankingEvent    => rank.id
      }
    val impressions   = groupedFeedback.process(ImpressionInjectFunction("impression", 30.minutes)).id("impressions")
    val clickthroughs = groupedFeedback.process(ClickthroughJoinFunction()).id("clickthroughs")
    val events        = raw.union(impressions)
    val writes        = events.flatMap(e => mapping.features.flatMap(_.writes(e))).id("expand-writes")
    val updates       = Featury.process(writes, featurySchema, 20.seconds).id("process-writes")
    val state         = updates.getSideOutput(FeatureProcessFunction.stateTag)
    Featury.writeState(state, new Path(s"${cmd.outDir}/state"), Compress.NoCompression).id("write-state")
    Featury
      .writeFeatures(updates, new Path(s"file://${cmd.outDir}/features"), Compress.NoCompression)
      .id("write-features")
    val joined = Featury.join[Clickthrough](updates, clickthroughs, CTJoin, mapping.underlyingSchema).id("timejoin")
    val computed =
      joined.map(ct => ct.copy(values = mapping.map(ct.ranking, ct.features, ct.interactions))).id("values")
    computed.sinkTo(DatasetSink(mapping, s"file://${cmd.outDir}/dataset")).id("write-train")
    streamEnv.execute("bootstrap")

    logger.info("processing done, generating savepoint")
    val batch = ExecutionEnvironment.getExecutionEnvironment
    batch.setParallelism(cmd.parallelism)
    val stateSource = Featury.readState(batch, new Path(s"${cmd.outDir}/state"), Compress.NoCompression)
    val transform = OperatorTransformation
      .bootstrapWith(stateSource.toJava)
      .keyBy(StateKeySelector, deriveTypeInformation[Key])
      .transform(new FeatureBootstrapFunction(mapping.underlyingSchema))

    Savepoint
      .create(new EmbeddedRocksDBStateBackend(), 32)
      .withOperator("feature-process", transform)
      .write(s"${cmd.outDir}/savepoint")

    batch.execute("savepoint")
    logger.info("done")
  }
}
