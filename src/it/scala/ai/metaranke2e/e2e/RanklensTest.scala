package ai.metaranke2e.e2e

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.flow.DataStreamOps._
import ai.metarank.mode.FileLoader
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.mode.inference.RedisEndpoint.EmbeddedRedis
import ai.metarank.mode.inference.api.RankApi
import ai.metarank.mode.inference.{FeatureStoreResource, FeedbackFlow, FlinkMinicluster, Inference}
import ai.metarank.mode.train.Train
import ai.metarank.mode.upload.Upload
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{Event, EventId}
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.{FlinkTest, RanklensEvents}
import better.files.File
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.findify.featury.connector.redis.RedisStore
import io.findify.featury.flink.format.BulkCodec
import io.findify.featury.model.api.{ReadRequest, ReadResponse}
import io.findify.featury.model.{FeatureValue, Key, Timestamp}
import io.findify.featury.values.ValueStoreConfig.RedisConfig
import io.findify.featury.values.{FeatureStore, StoreCodec}
import io.findify.flinkadt.api._
import org.apache.commons.io.IOUtils
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.configuration.Configuration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import scala.language.higherKinds
import scala.util.Random

class RanklensTest extends AnyFlatSpec with Matchers with FlinkTest {
  import ai.metarank.mode.TypeInfos._
  val baseConfig = Config
    .load(IOUtils.resourceToString("/ranklens/config.yml", StandardCharsets.UTF_8))
    .unsafeRunSync()

  val config = baseConfig.copy(
    bootstrap = baseConfig.bootstrap.copy(workdir = MPath(File.newTemporaryDirectory()))
  )

  val mapping = FeatureMapping.fromFeatureSchema(config.features, config.models)

  it should "accept events" in {
    env.setRuntimeMode(RuntimeExecutionMode.BATCH)

    val events = RanklensEvents()

    val source = env.fromCollection(events).watermark(_.timestamp.ts)
    Bootstrap.makeBootstrap(source, mapping, config.bootstrap.workdir)
    env.execute("bootstrap")
  }

  it should "generate savepoint" in {
    val batch = ExecutionEnvironment.getExecutionEnvironment

    Bootstrap.makeSavepoint(batch, config.bootstrap.workdir, mapping)
  }

  it should "train the xgboost model" in {
    train("xgboost")
  }

  // issue with lack of enthropy on ubuntu@GHA
//  it should "train the lightgbm model" in {
//    train("lightgbm")
//  }

  def train(modelName: String) = {
    val model         = mapping.models(modelName).asInstanceOf[LambdaMARTModel]
    val dataset       = Train.loadData(config.bootstrap.workdir, model.datasetDescriptor, modelName).unsafeRunSync()
    val (train, test) = Train.split(dataset, 80)
    FileLoader.write(model.conf.path, Map.empty, model.train(train, test).get).unsafeRunSync()
  }

  it should "rerank things" in {
    val ranking = RankingEvent(
      id = EventId("event1"),
      timestamp = Timestamp(1636993838000L),
      user = Some(UserId("u1")),
      session = Some(SessionId("s1")),
      items = NonEmptyList.of(
        ItemRelevancy(ItemId("7346"), 0.0),
        ItemRelevancy(ItemId("1971"), 0.0),
        ItemRelevancy(ItemId("69844"), 0.0),
        ItemRelevancy(ItemId("1246"), 0.0),
        ItemRelevancy(ItemId("3243"), 0.0),
        ItemRelevancy(ItemId("1644"), 0.0),
        ItemRelevancy(ItemId("6593"), 0.0),
        ItemRelevancy(ItemId("2599"), 0.0),
        ItemRelevancy(ItemId("3916"), 0.0)
      )
    )
    val interaction = InteractionEvent(
      id = EventId("event2"),
      item = ItemId("69844"),
      timestamp = Timestamp(1636993838000L),
      user = Some(UserId("u1")),
      session = Some(SessionId("s1")),
      `type` = "click",
      ranking = Some(EventId("event1"))
    )
    val port  = 1024 + Random.nextInt(10000)
    val redis = EmbeddedRedis.createUnsafe(port)

    val store = FeatureStoreResource
      .unsafe(() => RedisStore(RedisConfig("localhost", port, StoreCodec.JsonCodec)))
      .unsafeRunSync()

    val uploaded =
      Upload
        .upload(config.bootstrap.workdir / "features", "localhost", port, StoreCodec.JsonCodec)
        .allocated
        .unsafeRunSync()

    val rankers   = Inference.loadModels(config).unsafeRunSync()
    val ranker    = RankApi(mapping, store, rankers)
    val response1 = ranker.rerank(ranking, "xgboost", true).unsafeRunSync()
    response1.state.session shouldBe empty

    val cluster = FlinkMinicluster.createCluster(new Configuration()).unsafeRunSync()
    val flow = FeedbackFlow
      .resource(
        cluster = cluster,
        mapping = mapping,
        redisHost = "localhost",
        redisPort = port,
        savepoint = config.bootstrap.workdir / "savepoint",
        format = StoreCodec.JsonCodec,
        events = _.fromCollection(List[Event](ranking, interaction))
      )
      .allocated
      .unsafeRunSync()
      ._1
    Upload.blockUntilFinished(cluster, flow).unsafeRunSync()

    val response2 = ranker.rerank(ranking, "xgboost", true).unsafeRunSync()
    response2.state.session should not be empty
    response1.items.map(_.score) shouldNot be(response2.items.map(_.score))

//    val response3 = ranker.rerank(ranking, "lightgbm", true).unsafeRunSync()
//    response3.state.session should not be empty

    redis.close()
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
