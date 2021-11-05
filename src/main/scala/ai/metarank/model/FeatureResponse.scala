package ai.metarank.model

import io.findify.featury.model.{FeatureValue, Key}

case class FeatureResponse(values: Map[Key, FeatureValue])
