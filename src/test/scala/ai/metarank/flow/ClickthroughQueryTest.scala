package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.ml.rank.LambdaMARTRanker.{LambdaMARTConfig, LambdaMARTPredictor}
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Dimension.VectorDim
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.{ItemId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.ScopeType._
import ai.metarank.model.TrainValues.ClickthroughValues
import ai.metarank.model._
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ClickthroughQueryTest extends AnyFlatSpec with Matchers {
  lazy val features = List(
    NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType),
    StringFeatureSchema(
      FeatureName("category"),
      FieldName(Item, "category"),
      ItemScopeType,
      Some(IndexEncoderName),
      NonEmptyList.of("socks", "shirts")
    ),
    RateFeatureSchema(FeatureName("ctr"), "impression", "click", ItemScopeType, 24.hours, List(7, 30)),
    InteractedWithSchema(
      FeatureName("clicked_category"),
      "click",
      List(FieldName(Item, "category")),
      SessionScopeType,
      Some(10),
      Some(24.hours)
    )
  )
  lazy val model = LambdaMARTConfig(
    backend = XGBoostConfig(),
    features = NonEmptyList.fromListUnsafe(features.map(_.name)),
    weights = Map("click" -> 1)
  )
  lazy val mapping = FeatureMapping
    .fromFeatureSchema(
      schema = features,
      models = Map("xgboost" -> model)
    )
    .unsafeRunSync()

  val now = Timestamp.now

  it should "set explicit labels" in {
    val ct = ClickthroughValues(
      ct = Clickthrough(
        id = EventId("i1"),
        ts = now,
        user = Some(UserId("u1")),
        session = None,
        items = List(ItemId("p1"), ItemId("p2")),
        interactions =
          List(TypedInteraction(ItemId("p1"), "rel1", Some(1)), TypedInteraction(ItemId("p2"), "rel0", Some(0)))
      ),
      values = List(
        ItemValue(
          ItemId("p1"),
          List(
            CategoryValue(FeatureName("category"), "socks", 1),
            VectorValue(FeatureName("ctr"), Array(0.2, 0.1), 2),
            SingleValue(FeatureName("price"), 10.0),
            VectorValue(FeatureName("clicked_category"), Array(1.0), VectorDim(1))
          )
        ),
        ItemValue(
          ItemId("p2"),
          List(
            SingleValue(FeatureName("price"), 5.0),
            VectorValue(FeatureName("ctr"), Array(0.1, 0.05), 2),
            CategoryValue(FeatureName("category"), "shirts", 2),
            VectorValue(FeatureName("clicked_category"), Array(0.0), VectorDim(1))
          )
        )
      )
    )
    val query = ClickthroughQuery(
      ct.values,
      ct.ct.interactions,
      1,
      model.weights,
      mapping.models("xgboost").asInstanceOf[LambdaMARTPredictor].desc
    )
    query.labels.toList shouldBe List(1.0, 0.0)
  }

  it should "convert ranking+impression to query" in {
    val ct = ClickthroughValues(
      ct = Clickthrough(
        id = EventId("i1"),
        ts = now,
        user = Some(UserId("u1")),
        session = None,
        items = List(ItemId("p1"), ItemId("p2"), ItemId("p3")),
        interactions = List(TypedInteraction(ItemId("p2"), "click"))
      ),
      values = List(
        ItemValue(
          ItemId("p1"),
          List(
            CategoryValue(FeatureName("category"), "socks", 1),
            VectorValue(FeatureName("ctr"), Array(0.2, 0.1), 2),
            SingleValue(FeatureName("price"), 10.0),
            VectorValue(FeatureName("clicked_category"), Array(1.0), VectorDim(1))
          )
        ),
        ItemValue(
          ItemId("p2"),
          List(
            SingleValue(FeatureName("price"), 5.0),
            VectorValue(FeatureName("ctr"), Array(0.1, 0.05), 2),
            CategoryValue(FeatureName("category"), "shirts", 2),
            VectorValue(FeatureName("clicked_category"), Array(0.0), VectorDim(1))
          )
        ),
        ItemValue(
          ItemId("p3"),
          List(
            VectorValue(FeatureName("ctr"), Array(0.2, 0.2), 2),
            VectorValue(FeatureName("clicked_category"), Array(1.0), VectorDim(1)),
            SingleValue(FeatureName("price"), 3.0),
            CategoryValue(FeatureName("category"), "socks", 1)
          )
        )
      )
    )
    val query =
      ClickthroughQuery(
        ct.values,
        ct.ct.interactions,
        1,
        model.weights,
        mapping.models("xgboost").asInstanceOf[LambdaMARTPredictor].desc
      )
    query.labels.toList shouldBe List(0.0, 1.0, 0.0)
    query.columns shouldBe 5
    query.rows shouldBe 3
    query.values.toList shouldBe List(
      10.0, 1.0, 0.2, 0.1, 1.0, // p1
      5.0, 2.0, 0.1, 0.05, 0.0, // p2
      3.0, 1.0, 0.2, 0.2, 1.0   // p3
    )
  }
}
