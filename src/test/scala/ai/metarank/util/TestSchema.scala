package ai.metarank.util

import ai.metarank.FeatureMapping
import ai.metarank.config.ModelConfig.ShuffleConfig
import ai.metarank.model.{Env, FeatureSchema}
import cats.data.{NonEmptyList, NonEmptyMap}

object TestSchema {
  def apply(schema: FeatureSchema) = {
    FeatureMapping.fromFeatureSchema(Env.default, NonEmptyList.one(schema), NonEmptyMap.one("random", ShuffleConfig(1))).schema
  }
}
