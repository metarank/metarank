package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.config.Config.FeatureConfig
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.Aggregation

trait Feature {
  def values(conf: FeatureConfig, scope: Aggregation.Scope, store: Store): IO[List[Float]]
}
