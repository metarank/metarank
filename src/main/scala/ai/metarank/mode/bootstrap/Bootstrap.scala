package ai.metarank.mode.bootstrap

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.flow.{
  ClickthroughJoin,
  ClickthroughJoinFunction,
  DatasetSink,
  EventStateJoin,
  ImpressionInjectFunction
}
import ai.metarank.model.{Clickthrough, Event, EventId, EventState}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.source.{EventSource, FileEventSource}
import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.flink.util.Compress
import io.findify.featury.flink.{FeatureBootstrapFunction, FeatureProcessFunction, Featury}
import io.findify.featury.model.{FeatureValue, Key, Schema, State}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.scala.{DataStream, KeyedStream, StreamExecutionEnvironment}
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
  import ai.metarank.mode.TypeInfos._

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
    val mapping   = FeatureMapping.fromFeatureSchema(config.features, config.interactions)
    val streamEnv = StreamExecutionEnvironment.getExecutionEnvironment
    streamEnv.setParallelism(cmd.parallelism)
    streamEnv.setRuntimeMode(RuntimeExecutionMode.BATCH)

    logger.info("starting historical data processing")
    val raw: DataStream[Event] = FileEventSource(cmd.eventPath).eventStream(streamEnv).id("load")
    val grouped                = groupFeedback(raw)
    val (state, updates)       = makeUpdates(raw, grouped, mapping)

    Featury.writeState(state, new Path(s"${cmd.outDir}/state"), Compress.NoCompression).id("write-state")
    Featury
      .writeFeatures(updates, new Path(s"file://${cmd.outDir}/features"), Compress.NoCompression)
      .id("write-features")
    val computed = joinFeatures(updates, grouped, mapping)
    computed.sinkTo(DatasetSink.json(mapping, s"file://${cmd.outDir}/dataset")).id("write-train")
    streamEnv.execute("bootstrap")

    logger.info("processing done, generating savepoint")
    val batch = ExecutionEnvironment.getExecutionEnvironment
    batch.setParallelism(cmd.parallelism)
    val stateSource = Featury.readState(batch, new Path(s"${cmd.outDir}/state"), Compress.NoCompression)
    val transform = OperatorTransformation
      .bootstrapWith(stateSource.toJava)
      .keyBy(StateKeySelector, deriveTypeInformation[Key])
      .transform(new FeatureBootstrapFunction(mapping.schema))

    Savepoint
      .create(new EmbeddedRocksDBStateBackend(), 32)
      .withOperator("feature-process", transform)
      .write(s"${cmd.outDir}/savepoint")

    batch.execute("savepoint")
    logger.info("done")
  }

  def groupFeedback(raw: DataStream[Event]) = {
    raw
      .collect { case f: FeedbackEvent => f }
      .id("select-feedback")
      .keyingBy {
        case int: InteractionEvent => int.ranking
        case rank: RankingEvent    => rank.id
      }
  }

  def makeUpdates(raw: DataStream[Event], grouped: KeyedStream[FeedbackEvent, EventId], mapping: FeatureMapping) = {
    val impressions      = grouped.process(ImpressionInjectFunction("impression", 30.minutes)).id("impressions")
    val events           = raw.union(impressions)
    val statelessWrites  = events.flatMap(e => mapping.features.flatMap(_.writes(e))).id("expand-writes")
    val statelessUpdates = Featury.process(statelessWrites, mapping.schema, 20.seconds).id("process-writes")

    val eventsWithState =
      Featury.join[EventState](statelessUpdates, events.map(e => EventState(e)), EventStateJoin, mapping.statefulSchema)
    val statefulWrites  = eventsWithState.flatMap(e => mapping.statefulFeatures.flatMap(_.writes(e.event, e.state)))
    val statefulUpdates = Featury.process(statefulWrites, mapping.statefulSchema, 20.seconds)

    val state1 = statelessUpdates.getSideOutput(FeatureProcessFunction.stateTag)
    val state2 = statefulUpdates.getSideOutput(FeatureProcessFunction.stateTag)
    val state = state1
      .union(state2)
      .keyBy(_.key)
      .reduce((a, b) => if (a.ts.isAfter(b.ts)) a else b) // use only last state version
    val updates = statelessUpdates.union(statefulUpdates)
    (state, updates)
  }

  def joinFeatures(
      updates: DataStream[FeatureValue],
      grouped: KeyedStream[FeedbackEvent, EventId],
      mapping: FeatureMapping
  ) = {
    val clickthroughs = grouped.process(ClickthroughJoinFunction()).id("clickthroughs")
    val joined =
      Featury.join[Clickthrough](updates, clickthroughs, ClickthroughJoin, mapping.schema).id("timejoin")
    val computed =
      joined.map(ct => ct.copy(values = mapping.map(ct.ranking, ct.features, ct.interactions))).id("values")
    computed
  }
}
