package ai.metarank.mode

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.flow.ClickthroughQuery
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.{Clickthrough, ClickthroughValues, FieldName, ItemValue, Timestamp}
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.ScopeType._
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class TrainTest extends AnyFlatSpec with Matchers {
  lazy val features = NonEmptyList.of(
    NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType),
    StringFeatureSchema(
      FeatureName("category"),
      FieldName(Item, "category"),
      ItemScopeType,
      IndexEncoderName,
      NonEmptyList.of("socks", "shirts")
    ),
    RateFeatureSchema(FeatureName("ctr"), "impression", "click", 24.hours, List(7, 30), ItemScopeType),
    InteractedWithSchema(
      FeatureName("clicked_category"),
      "click",
      FieldName(Item, "category"),
      SessionScopeType,
      Some(10),
      Some(24.hours)
    )
  )
  lazy val model = LambdaMARTConfig(
    backend = XGBoostBackend(),
    features = features.map(_.name),
    weights = Map("click" -> 1)
  )
  lazy val mapping = FeatureMapping.fromFeatureSchema(
    schema = features,
    models = Map("xgboost" -> model)
  )

  val now = Timestamp.now
  it should "convert ranking+impression to query" in {
    val ct = ClickthroughValues(
      ct = Clickthrough(
        ts = now,
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
            SingleValue(FeatureName("clicked_category"), 1)
          )
        ),
        ItemValue(
          ItemId("p2"),
          List(
            SingleValue(FeatureName("price"), 5.0),
            VectorValue(FeatureName("ctr"), Array(0.1, 0.05), 2),
            CategoryValue(FeatureName("category"), "shirts", 2),
            SingleValue(FeatureName("clicked_category"), 0)
          )
        ),
        ItemValue(
          ItemId("p3"),
          List(
            VectorValue(FeatureName("ctr"), Array(0.2, 0.2), 2),
            SingleValue(FeatureName("clicked_category"), 1),
            SingleValue(FeatureName("price"), 3.0),
            CategoryValue(FeatureName("category"), "socks", 1)
          )
        )
      )
    )
    val query =
      ClickthroughQuery(ct.values, ct.ct.interactions, 1, model.weights, mapping.models("xgboost").datasetDescriptor)
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
