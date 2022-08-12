package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.{ItemScopeType, SessionScopeType}
import cats.data.{NonEmptyList, NonEmptyMap}

import scala.concurrent.duration._

object TestFeatureMapping {
  def apply() = {
    val features = NonEmptyList.of(
      NumberFeatureSchema(FeatureName("price"), FieldName(Item, "price"), ItemScopeType),
      WordCountSchema(FeatureName("title_length"), FieldName(Item, "title"), ItemScopeType),
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

    val models = NonEmptyMap.of(
      "random" -> LambdaMARTConfig(
        backend = XGBoostBackend(),
        features = features.map(_.name),
        weights = NonEmptyMap.of("click" -> 1)
      )
    )
    FeatureMapping.fromFeatureSchema(features, models)
  }
}
