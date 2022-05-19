package ai.metarank.mode.bootstrap

import ai.metarank.FeatureMapping
import ai.metarank.config.BootstrapConfig.SyntheticImpressionConfig
import ai.metarank.config.{Config, MPath}
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
import ai.metarank.rank.Model
import ai.metarank.source.EventSource
import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import io.findify.featury.flink.FeatureJoinFunction.FeatureJoinBootstrapFunction
import io.findify.featury.flink.format.{BulkCodec, BulkInputFormat, CompressedBulkReader, CompressedBulkWriter}
import io.findify.featury.flink.util.Compress
import io.findify.featury.flink.{FeatureBootstrapFunction, FeatureProcessFunction, Featury}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{FeatureValue, Key, Schema, State, Timestamp, Write}
import org.apache.flink.api.common.RuntimeExecutionMode
import io.findify.flink.api.{DataStream, KeyedStream, StreamExecutionEnvironment}
import io.findify.flinkadt.api._
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend
import org.apache.flink.state.api.{OperatorTransformation, Savepoint, SavepointWriter}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import io.findify.flink.api._
import io.findify.flinkadt.api._
import org.apache.flink.api.common.eventtime.WatermarkStrategy

object Bootstrap extends IOApp with Logging {
  import ai.metarank.flow.DataStreamOps._
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
    env <- IO { System.getenv().asScala.toMap }
    configContents <- args match {
      case configPath :: Nil => FileLoader.read(MPath(configPath), env)
      case _                 => IO.raiseError(new IllegalArgumentException("usage: metarank <config path>"))
    }
    config <- Config.load(new String(configContents))
    _      <- IO { logger.info("Performing bootstap.") }
    _      <- IO { logger.info(s"  workdir dir URL: ${config.bootstrap.workdir}") }
    _      <- run(config)
  } yield {
    ExitCode.Success
  }

  def run(config: Config) = IO {
    config.bootstrap.workdir match {
      case path: MPath.LocalPath if path.file.notExists =>
        logger.info(s"local dir $path does not exist, creating")
        path.file.createDirectoryIfNotExists(createParents = true)
      case _ => // none
    }
    val mapping = FeatureMapping.fromFeatureSchema(config.features, config.models)

    val streamEnv =
      StreamExecutionEnvironment.createLocalEnvironment(
        config.bootstrap.parallelism,
        FlinkS3Configuration(System.getenv())
      )
    streamEnv.setRuntimeMode(RuntimeExecutionMode.BATCH)
    streamEnv.getConfig.enableObjectReuse()
    logger.info("starting historical data processing")

    val raw: DataStream[Event] =
      EventSource.fromConfig(config.bootstrap.source).eventStream(streamEnv, bounded = true).id("load")
    makeBootstrap(raw, mapping, config.bootstrap.workdir, config.bootstrap.syntheticImpression)
    streamEnv.execute("bootstrap")

    logger.info("processing done, generating savepoint")

    makeSavepoint(streamEnv, config.bootstrap.workdir, mapping)
    logger.info("Bootstrap done")
  }

  def makeBootstrap(raw: DataStream[Event], mapping: FeatureMapping, dir: MPath, impress: SyntheticImpressionConfig) = {
    val grouped                  = groupFeedback(raw)
    val (state, fields, updates) = makeUpdates(raw, grouped, mapping, impress)

    val lastState    = selectLast[State, Key](state, _.key, _.ts).id("last-state")
    val lastFeatures = selectLast[FeatureValue, Key](updates, _.key, _.ts).id("last-features")
    // val lastFields = selectLast[FieldUpdate, FieldId](fields, _.id, )

    Featury.writeState(lastState, dir.child("state").flinkPath, Compress.NoCompression).id("write-state")
    Featury
      .writeFeatures(lastFeatures, dir.child("features").flinkPath, Compress.NoCompression)
      .id("write-features")
    val fieldsPath = dir.child("fields").flinkPath
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
    val clickthroughs = grouped.process(ClickthroughJoinFunction()).id(s"clickthroughs")
    val joined = Featury.join[Clickthrough](updates, clickthroughs, ClickthroughJoin, mapping.schema).id("join-state")

    mapping.models.foreach { case (name, model) =>
      val computed =
        joined
          .map(ct => ct.copy(values = model.featureValues(ct.ranking, ct.features, ct.interactions)))
          .id(s"$name-values")
      computed
        .sinkTo(DatasetSink.json(model.datasetDescriptor, dir.child(s"dataset-$name").uri))
        .id(s"$name-write-train")

    }
  }

  def selectLast[T, K: TypeInformation](stream: DataStream[T], key: T => K, ts: T => Timestamp): DataStream[T] = {
    stream.keyBy(event => key(event)).reduce((a, b) => if (ts(a).isAfter(ts(b))) a else b)
  }

  def groupFeedback(raw: DataStream[Event]) = {
    raw
      .flatMap { x =>
        x match {
          case f: FeedbackEvent => Some(f)
          case _                => None
        }
      }
      // .collect { case f: FeedbackEvent => f }
      .id("select-feedback")
      .keyBy { x =>
        x match {
          case int: InteractionEvent => int.ranking.getOrElse(EventId("0"))
          case rank: RankingEvent    => rank.id
        }
      }
  }

  def makeUpdates(
      raw: DataStream[Event],
      grouped: KeyedStream[FeedbackEvent, EventId],
      mapping: FeatureMapping,
      impress: SyntheticImpressionConfig
  ): (DataStream[State], DataStream[FieldUpdate], DataStream[FeatureValue]) = {
    val events = if (impress.enabled) {
      val impressions = grouped.process(ImpressionInjectFunction(impress.eventName, 30.minutes)).id("impressions")
      raw.union(impressions)
    } else {
      raw
    }

    val fieldUpdates = raw.flatMap(e => FieldUpdate.fromEvent(e)).id("field-updates")

    val writes: DataStream[Write] =
      events
        .connect(fieldUpdates.broadcast(fieldState))
        .process(EventProcessFunction(fieldState, mapping))
        .id("process-events")
    val updates = Featury.process(writes, mapping.schema, 20.seconds).id("process-writes")
    val state   = updates.getSideOutput(FeatureProcessFunction.stateTag)
    (state, fieldUpdates, updates)
  }

  def makeSavepoint(env: StreamExecutionEnvironment, dir: MPath, mapping: FeatureMapping) = {
    val fieldsPath = dir / "fields"
    val fieldsSource = env
      .fromSource(
        CompressedBulkReader.readFile(fieldsPath.flinkPath, Compress.NoCompression, FieldUpdateCodec),
        WatermarkStrategy.noWatermarks(),
        "read-fields"
      )

    val stateSource = env.fromSource(
      Featury.readState(dir.child("state").flinkPath, Compress.NoCompression),
      WatermarkStrategy.noWatermarks(),
      "read-state"
    )

    val valuesPath = dir / "features"
    val valuesSource = env.fromSource(
      Featury.readFeatures(
        path = valuesPath.flinkPath,
        codec = BulkCodec.featureValueProtobufCodec,
        compress = Compress.NoCompression
      ),
      WatermarkStrategy.noWatermarks(),
      "read-features"
    )

    val transformStateJoin = OperatorTransformation
      .bootstrapWith(valuesSource.javaStream)
      .keyBy(FeatureValueKeySelector, deriveTypeInformation[Tenant])
      .transform(new FeatureJoinBootstrapFunction())

    val transformFeatures = OperatorTransformation
      .bootstrapWith(stateSource.javaStream)
      .keyBy(StateKeySelector, deriveTypeInformation[Key])
      .transform(new FeatureBootstrapFunction(mapping.schema))

    val transformFields = OperatorTransformation
      .bootstrapWith(fieldsSource.javaStream)
      .transform(FieldValueBootstrapFunction(fieldState))

    val backend = new HashMapStateBackend()
    SavepointWriter
      .newSavepoint(backend, 32)
      .withOperator("process-writes", transformFeatures)
      .withOperator("join-state", transformStateJoin)
      .withOperator("process-events", transformFields)
      .write(dir.child("savepoint").uri)

    env.execute("savepoint")
  }
}
