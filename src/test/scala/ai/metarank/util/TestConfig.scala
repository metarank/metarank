package ai.metarank.util

import ai.metarank.config.{Config, IngestConfig}
import ai.metarank.model.FeatureSchema.NumberFeatureSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.Metadata

object TestConfig {
  def apply() = new Config(
    feature = List(NumberFeatureSchema("price", FieldName(Metadata, "price"), ItemScope))
  )
}
