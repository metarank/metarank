package ai.metarank.main

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.{LightGBMBackend, XGBoostBackend}
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.main.TrainTest._
import ai.metarank.main.command.{Import, Train}
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.{Event, EventId, FieldName, Timestamp}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.rank.LambdaMARTModel
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.util.Random

class TrainTest extends AnyFlatSpec with Matchers {
  lazy val mapping = createMapping()
  lazy val store   = MemPersistence(mapping.schema)

  it should "generate test data" in {
    Import.slurp(fs2.Stream.emits(createDataset(1000)), store, mapping).unsafeRunSync()
  }

  it should "train xgboost model" in {
    val result = train("xgboost")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 2
  }

  it should "train lightgbm model" in {
    val result = train("lightgbm")
    result.iterations.size shouldBe 10
    result.features.size shouldBe 2
  }

  def train(name: String) = {
    val model = mapping.models(name).asInstanceOf[LambdaMARTModel]
    Train.train(store, model, "xgboost", model.conf.backend).unsafeRunSync()
  }
}

object TrainTest {
  def createMapping() = {
    val features = NonEmptyList.of(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType, refresh = Some(1.minute)),
      WordCountSchema(FeatureName("title_length"), FieldName(Item, "title"), ItemScopeType)
    )

    val models = Map(
      "xgboost" -> LambdaMARTConfig(
        backend = XGBoostBackend(iterations = 10),
        features = features.map(_.name),
        weights = Map("click" -> 1)
      ),
      "lightgbm" -> LambdaMARTConfig(
        backend = LightGBMBackend(iterations = 10),
        features = features.map(_.name),
        weights = Map("click" -> 1)
      )
    )
    FeatureMapping.fromFeatureSchema(features, models)
  }

  def createDataset(size: Int): List[Event] = {
    var ts = System.currentTimeMillis() - (size * 3000)
    val events = for {
      i <- 0 until size
    } yield {
      ts += 3000
      List(
        TestItemEvent(
          s"p$i",
          List(
            StringField("title", (0 until Random.nextInt(10)).map(_ => "foo").mkString(" ")),
            NumberField("price", Random.nextInt(100))
          )
        ).copy(timestamp = Timestamp(ts)),
        TestRankingEvent(List(s"p${Random.nextInt(size)}", s"p$i", s"p${Random.nextInt(size)}"))
          .copy(timestamp = Timestamp(ts + 1000), id = EventId(s"id$i")),
        TestInteractionEvent(s"p$i", s"id$i").copy(timestamp = Timestamp(ts + 2000))
      )
    }
    events.flatten.toList
  }
}
