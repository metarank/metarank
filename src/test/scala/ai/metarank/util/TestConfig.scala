package ai.metarank.util

import ai.metarank.config.Config.{InteractionConfig, ModelConfig}
import ai.metarank.config.Config
import ai.metarank.config.Config.ModelConfig.Shuffle
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import cats.data.{NonEmptyList, NonEmptyMap}

object TestConfig {
  def apply() = new Config(
    features = NonEmptyList.of(NumberFeatureSchema("price", FieldName(Item, "price"), ItemScope)),
    interactions = NonEmptyList.of(InteractionConfig("click", 1.0)),
    models = Map("shuffle" -> Shuffle())
  )
}
