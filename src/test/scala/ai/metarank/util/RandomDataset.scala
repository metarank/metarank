package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.config.BoosterConfig.{LightGBMConfig, XGBoostConfig}
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.{Event, EventId, FieldName, Timestamp}
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global

import scala.concurrent.duration._
import scala.util.Random

case class RandomDataset(mapping: FeatureMapping, events: List[Event])

object RandomDataset {
  def generate(size: Int) = {
    val features = List(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType, refresh = Some(1.minute)),
      WordCountSchema(FeatureName("title_length"), FieldName(Item, "title"), ItemScopeType)
    )

    val models = Map(
      "xgboost" -> LambdaMARTConfig(
        backend = XGBoostConfig(iterations = 10),
        features = NonEmptyList.fromListUnsafe(features.map(_.name)),
        weights = Map("click" -> 1)
      ),
      "xgboost1" -> LambdaMARTConfig(
        backend = XGBoostConfig(iterations = 10),
        features = NonEmptyList.one(features.map(_.name).head),
        weights = Map("click" -> 1)
      ),
      "lightgbm" -> LambdaMARTConfig(
        backend = LightGBMConfig(iterations = 10),
        features = NonEmptyList.fromListUnsafe(features.map(_.name)),
        weights = Map("click" -> 1)
      )
    )
    val mapping = FeatureMapping.fromFeatureSchema(features, models).unsafeRunSync()
    var ts      = System.currentTimeMillis() - (size * 3000)
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
    RandomDataset(mapping, events.flatten.toList)
  }
}
