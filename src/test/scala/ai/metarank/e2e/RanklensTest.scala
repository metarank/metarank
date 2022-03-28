package ai.metarank.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.Config.InteractionConfig
import ai.metarank.e2e.RanklensTest.DiskStore
import ai.metarank.feature.WordCountFeature
import ai.metarank.model.{Clickthrough, Event, EventState, FieldName, ItemId, UserId}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.FieldName.Metadata
import ai.metarank.util.{FlinkTest, RanklensEvents}
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.findify.featury.flink.{Featury, Join}
import org.apache.flink.api.common.RuntimeExecutionMode
import io.findify.flinkadt.api._
import ai.metarank.flow.DataStreamOps._

import scala.language.higherKinds
import scala.concurrent.duration._
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.flow.{
  ClickthroughJoin,
  ClickthroughJoinFunction,
  DatasetSink,
  EventStateJoin,
  ImpressionInjectFunction
}
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.mode.bootstrap.Bootstrap.{joinFeatures, makeUpdates}
import ai.metarank.mode.inference.FeatureStoreResource
import ai.metarank.mode.inference.api.RankApi
import ai.metarank.mode.inference.ranking.LightGBMScorer
import ai.metarank.mode.train.Train
import ai.metarank.mode.train.Train.{logger, split, trainModel}
import ai.metarank.mode.train.TrainCmdline.LambdaMARTLightGBM
import better.files.{File, Resource}
import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import io.findify.featury.flink.format.BulkCodec
import io.findify.featury.flink.util.Compress
import io.findify.featury.model.api.{ReadRequest, ReadResponse}
import io.findify.featury.model.{FeatureValue, Key}
import io.findify.featury.values.FeatureStore
import org.apache.commons.io.IOUtils
import org.apache.flink.core.fs.Path

import java.nio.charset.StandardCharsets

class RanklensTest extends AnyFlatSpec with Matchers with FlinkTest {
  import ai.metarank.mode.TypeInfos._
  val config   = Config.load(IOUtils.resourceToString("/ranklens/config.yml", StandardCharsets.UTF_8)).unsafeRunSync()
  lazy val dir = File.newTemporaryDirectory("train_json_")
  lazy val modelFile  = File.newTemporaryFile("model_")
  lazy val updatesDir = File.newTemporaryDirectory("updates_")
  val mapping         = FeatureMapping.fromFeatureSchema(config.features, config.interactions)

  it should "accept events" in {
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val events = RanklensEvents()

    val source  = env.fromCollection(events).watermark(_.timestamp.ts)
    val grouped = Bootstrap.groupFeedback(source)
    val updates = Bootstrap.makeUpdates(source, grouped, mapping)._2
    Featury.writeFeatures(updates, new Path(updatesDir.toString()), Compress.NoCompression)
    val computed = Bootstrap.joinFeatures(updates, grouped, mapping)

    computed.sinkTo(DatasetSink.json(mapping, s"file:///$dir"))
    env.execute()
  }

  // fails, see https://github.com/metarank/metarank/issues/338
  it should "train the model" ignore {
    val dataset       = Train.loadData(dir, mapping.datasetDescriptor).unsafeRunSync()
    val (train, test) = Train.split(dataset, 80)
    modelFile.write(Train.trainModel(train, test, LambdaMARTLightGBM, 200))
  }

  it should "rerank things" in {
    val event = RanklensEvents().collect { case r: RankingEvent =>
      r
    }.head
    val model    = IOUtils.toString(Resource.my.getAsStream("/ranklens/ranklens.model"), StandardCharsets.UTF_8)
    val store    = FeatureStoreResource.unsafe(() => DiskStore(updatesDir)).unsafeRunSync()
    val ranker   = RankApi(mapping, store, LightGBMScorer(model))
    val response = ranker.rerank(event, false).unsafeRunSync()
    val br       = 1
  }
}

object RanklensTest {
  case class DiskStore(map: Map[Key, FeatureValue]) extends FeatureStore {
    override def read(request: ReadRequest): IO[ReadResponse] = IO {
      val values = for {
        key   <- request.keys
        value <- map.get(key)
      } yield {
        value
      }
      ReadResponse(values)
    }

    override def write(batch: List[FeatureValue]): IO[Unit] = ???

    override def close(): IO[Unit] = IO.unit
  }

  object DiskStore {
    def apply(path: File): DiskStore = {
      val values = for {
        file <- path.listRecursively.filter(_.extension(includeDot = false).contains("pb"))
        stream = file.newFileInputStream
        value <- Iterator.continually(BulkCodec.featureValueProtobufCodec.read(stream)).takeWhile(_.isDefined).flatten
      } yield {
        value.key -> value
      }
      DiskStore(values.toMap)
    }
  }
}
