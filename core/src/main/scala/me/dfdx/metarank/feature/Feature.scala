package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.config.Config.TrackerConfig
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.Tracker

trait Feature {
  def values(scope: Tracker.Scope, store: Store): IO[Array[Float]]
}
