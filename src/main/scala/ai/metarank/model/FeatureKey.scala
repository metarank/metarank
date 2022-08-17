package ai.metarank.model

import ai.metarank.model.Key.FeatureName

case class FeatureKey(scope: ScopeType, feature: FeatureName)

object FeatureKey {
  def apply(key: Key): FeatureKey = FeatureKey(key.scope.getType, key.feature)
}
