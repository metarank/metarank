package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.config.MPath
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.feature.StringFeature.EncoderName.IndexEncoderName
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope}
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}

import scala.concurrent.duration._

object TestFeatureMapping {
  def apply() = {
    val features = NonEmptyList.of(
      NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope),
      WordCountSchema("title_length", FieldName(Item, "title"), ItemScope),
      StringFeatureSchema(
        "category",
        FieldName(Item, "category"),
        ItemScope,
        IndexEncoderName,
        NonEmptyList.of("socks", "shirts")
      ),
      RateFeatureSchema("ctr", "impression", "click", 24.hours, List(7, 30), ItemScope),
      InteractedWithSchema(
        "clicked_category",
        "click",
        FieldName(Item, "category"),
        SessionScope,
        Some(10),
        Some(24.hours)
      )
    )

    val models = NonEmptyMap.of(
      "random" -> LambdaMARTConfig(
        path = MPath(File.newTemporaryFile().deleteOnExit()),
        backend = XGBoostBackend(),
        features = features.map(_.name),
        weights = NonEmptyMap.of("click" -> 1)
      )
    )
    FeatureMapping.fromFeatureSchema(features, models)
  }
}
