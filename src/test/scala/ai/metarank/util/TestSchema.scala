package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.ml.rank.ShuffleRanker.ShuffleConfig
import ai.metarank.model.FeatureSchema
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.unsafe.implicits.global

object TestSchema {
  def apply(schema: FeatureSchema) = {
    FeatureMapping.fromFeatureSchema(List(schema), Map("random" -> ShuffleConfig(1))).unsafeRunSync().schema
  }
}
