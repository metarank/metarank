package ai.metarank.util

import ai.metarank.config.{Config, IngestConfig}
import ai.metarank.model.FeatureSchema.NumberFeatureSchema
import ai.metarank.model.FeatureSource

object TestConfig {
  def apply() = new Config(
    feature = List(NumberFeatureSchema("price", "price", FeatureSource.Metadata))
  )
}
