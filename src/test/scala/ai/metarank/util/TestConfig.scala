package ai.metarank.util

import ai.metarank.config.Config.InteractionConfig
import ai.metarank.config.Config
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.Metadata

object TestConfig {
  def apply() = new Config(
    features = List(NumberFeatureSchema("price", FieldName(Metadata, "price"), ItemScope)),
    interactions = List(InteractionConfig("click", 1.0))
  )
}
