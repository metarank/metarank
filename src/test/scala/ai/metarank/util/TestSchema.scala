package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.ml.rank.ShuffleRanker.ShuffleConfig
import ai.metarank.model.FeatureSchema
import cats.data.{NonEmptyList, NonEmptyMap}

object TestSchema {
  def apply(schema: FeatureSchema) = {
    FeatureMapping.fromFeatureSchema(NonEmptyList.one(schema), Map("random" -> ShuffleConfig(1))).schema
  }
}
