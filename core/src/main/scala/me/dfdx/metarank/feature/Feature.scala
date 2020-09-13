package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.config.FeatureConfig

trait Feature {
  def values(scope: Aggregation.Scope, store: Store): IO[List[Float]]
}
