package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.config.Config
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.Tracker
import me.dfdx.metarank.tracker.state.CircularReservoir

class WindowCountFeature extends Feature {
  override def values(scope: Tracker.Scope, store: Store): IO[Array[Float]] = {
    for {}
  }
}
