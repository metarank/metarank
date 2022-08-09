package ai.metarank.model

import ai.metarank.model.Key.FeatureName

case class FeatureKey(env: Env, scope: ScopeType, feature: FeatureName)

object FeatureKey {
  def apply(key: Key): FeatureKey = FeatureKey(key.scope.env, key.scope.getType, key.feature)
}
