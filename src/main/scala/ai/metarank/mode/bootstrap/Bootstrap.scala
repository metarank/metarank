package ai.metarank.mode.bootstrap

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.flow.{
  ClickthroughJoin,
  ClickthroughJoinFunction,
  DatasetSink,
  EventProcessFunction,
  EventStateJoin,
  ImpressionInjectFunction
}
import ai.metarank.mode.{FileLoader, FlinkS3Configuration}
import ai.metarank.model.{Clickthrough, Event, EventId, EventState, Field, FieldId, FieldUpdate}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.source.{EventSource, FileEventSource}
import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.flink.FeatureJoinFunction.FeatureJoinBootstrapFunction
import io.findify.featury.flink.format.{BulkCodec, BulkInputFormat, CompressedBulkWriter}
import io.findify.featury.flink.util.Compress
import io.findify.featury.flink.{FeatureBootstrapFunction, FeatureProcessFunction, Featury}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Key, Schema, State, Write}
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.streaming.api.scala.{DataStream, KeyedStream, StreamExecutionEnvironment}
import io.findify.flinkadt.api._
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.contrib.streaming.state.{EmbeddedRocksDBStateBackend, RocksDBOptionsFactory}
import org.apache.flink.core.fs.Path
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend
import org.apache.flink.state.api.{OperatorTransformation, Savepoint}
import org.apache.flink.streaming.api.scala.extensions.acceptPartialFunctions
import org.rocksdb.{ColumnFamilyOptions, DBOptions}

import java.util
import scala.language.higherKinds
import scala.concurrent.duration._
import scala.collection.JavaConverters._

object Bootstrap extends IOApp with Logging {
  import ai.metarank.flow.DataStreamOps._
  import org.apache.flink.DataSetOps._
  import ai.metarank.mode.TypeInfos._

  case object StateKeySelector extends KeySelector[State, Key] { override def getKey(value: State): Key = value.key }
  case object FeatureValueKeySelector extends KeySelector[FeatureValue, Tenant] {
    override def getKey(value: FeatureValue): Tenant = value.key.tenant
  }

  lazy val fieldState = new MapStateDescriptor[FieldId, Field](
    "fields",
    implicitly[TypeInformation[FieldId]],
    implicitly[TypeInformation[Field]]
  )

  override def run(args: List[String]): IO[ExitCode] = for {
    env            <- IO { System.getenv().asScala.toMap }
    cmd            <- BootstrapCmdline.parse(args, env)
    _              <- IO { logger.info("Performing bootstap.") }
    _              <- IO { logger.info(s"  events URL: ${cmd.eventPathUrl}") }
    _              <- IO { logger.info(s"  output dir URL: ${cmd.outDirUrl}") }
    _              <- IO { logger.info(s"  config: ${cmd.config}") }
    configContents <- FileLoader.loadLocal(cmd.config, env)
    config         <- Config.load(new String(configContents))
    _              <- run(config, cmd)
  } yield {
    ExitCode.Success
  }

  def run(config: Config, cmd: BootstrapCmdline) = IO {
    if (cmd.outDirUrl.startsWith("file://")) { File(cmd.outDir).createDirectoryIfNotExists(createParents = true) }
    val mapping = FeatureMapping.fromFeatureSchema(config.features, config.interactions)

    val streamEnv =
      StreamExecutionEnvironment.createLocalEnvironment(cmd.parallelism, FlinkS3Configuration(System.getenv()))
    streamEnv.setRuntimeMode(RuntimeExecutionMode.BATCH)
    streamEnv.getConfig.enableObjectReuse()
    logger.info("starting historical data processing")

    val raw: DataStream[Event] = FileEventSource(cmd.eventPathUrl).eventStream(streamEnv).id("load")
    makeBootstrap(raw, mapping, cmd.outDirUrl)
    streamEnv.execute("bootstrap")

    logger.info("processing done, generating savepoint")
    val batch = ExecutionEnvironment.getExecutionEnvironment
    batch.setParallelism(cmd.parallelism)

    makeSavepoint(batch, cmd.outDirUrl, mapping)
    logger.info("Bootstrap done")
  }

  def makeBootstrap(raw: DataStream[Event], mapping: FeatureMapping, dir: String) = {
    val grouped                  = groupFeedback(raw)
    val (state, fields, updates) = makeUpdates(raw, grouped, mapping)

    Featury.writeState(state, new Path(s"$dir/state"), Compress.NoCompression).id("write-state")
    Featury
      .writeFeatures(updates, new Path(s"$dir/features"), Compress.NoCompression)
      .id("write-features")
    val fieldsPath = new Path(s"$dir/fields")
    fields
      .sinkTo(
        CompressedBulkWriter.writeFile(
          path = fieldsPath,
          compress = Compress.NoCompression,
          codec = FieldUpdateCodec,
          prefix = "fields"
        )
      )
      .id("write-fields")
    val computed = joinFeatures(updates, grouped, mapping)
    computed.sinkTo(DatasetSink.json(mapping, s"$dir/dataset")).id("write-train")
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

  def makeUpdates(
      raw: DataStream[Event],
      grouped: KeyedStream[FeedbackEvent, EventId],
      mapping: FeatureMapping
  ): (DataStream[State], DataStream[FieldUpdate], DataStream[FeatureValue]) = {
    val impressions = grouped.process(ImpressionInjectFunction("impression", 30.minutes)).id("impressions")
    val events      = raw.union(impressions)

    val fieldUpdates = raw.flatMap(e => FieldUpdate.fromEvent(e))

    val writes: DataStream[Write] =
      events
        .connect(fieldUpdates.broadcast(fieldState))
        .process(EventProcessFunction(fieldState, mapping))
        .id("process-events")
    val updates = Featury.process(writes, mapping.schema, 20.seconds).id("process-writes")
    val state   = updates.getSideOutput(FeatureProcessFunction.stateTag)
    (state, fieldUpdates, updates)
  }

  def joinFeatures(
      updates: DataStream[FeatureValue],
      grouped: KeyedStream[FeedbackEvent, EventId],
      mapping: FeatureMapping
  ) = {
    val clickthroughs = grouped.process(ClickthroughJoinFunction()).id("clickthroughs")
    val joined =
      Featury.join[Clickthrough](updates, clickthroughs, ClickthroughJoin, mapping.schema).id("join-state")
    val computed =
      joined.map(ct => ct.copy(values = mapping.map(ct.ranking, ct.features, ct.interactions))).id("values")
    computed
  }

  def makeSavepoint(batch: ExecutionEnvironment, dir: String, mapping: FeatureMapping) = {
    val stateSource = Featury.readState(batch, new Path(s"$dir/state"), Compress.NoCompression)

    val valuesPath = s"$dir/features"
    val valuesSource = batch
      .readFile(
        new BulkInputFormat[FeatureValue](
          path = new Path(valuesPath),
          codec = BulkCodec.featureValueProtobufCodec,
          compress = Compress.NoCompression
        ),
        valuesPath
      )
      .name("read-features")

    val fieldsPath = new Path(s"$dir/fields")
    val fieldsSource = batch
      .readFile(
        new BulkInputFormat[FieldUpdate](
          path = fieldsPath,
          codec = FieldUpdateCodec,
          compress = Compress.NoCompression
        ),
        fieldsPath.getPath
      )
      .name("read-fields")

    val transformStateJoin = OperatorTransformation
      .bootstrapWith(valuesSource.toJava)
      .keyBy(FeatureValueKeySelector, deriveTypeInformation[Tenant])
      .transform(new FeatureJoinBootstrapFunction())

    val transformFeatures = OperatorTransformation
      .bootstrapWith(stateSource.toJava)
      .keyBy(StateKeySelector, deriveTypeInformation[Key])
      .transform(new FeatureBootstrapFunction(mapping.schema))

    val transformFields = OperatorTransformation
      .bootstrapWith(fieldsSource.toJava)
      .transform(FieldValueBootstrapFunction(fieldState))

    val backend = new HashMapStateBackend()
    Savepoint
      .create(backend, 32)
      .withOperator("process-writes", transformFeatures)
      .withOperator("join-state", transformStateJoin)
      .withOperator("process-events", transformFields)
      .write(s"$dir/savepoint")

    batch.execute("savepoint")
  }
}
