package ai.metarank.model

import ai.metarank.model.Key.{FeatureName, Scope}

case class FeatureKey(scope: Scope, feature: FeatureName)

object FeatureKey {
  def apply(key: Key): FeatureKey = FeatureKey(key.tag.scope, key.name)
}
