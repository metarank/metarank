package me.dfdx.metarank.tracker

import cats.effect.IO
import me.dfdx.metarank.config.Config.TrackerConfig
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.state.CircularReservoir

object WindowCountTracker extends Tracker[CircularReservoir] {
  override val name = "window_count"

  override def readState(config: TrackerConfig, scope: Tracker.Scope, store: Store): IO[CircularReservoir] =
    store.load[CircularReservoir](name, scope).map(_.getOrElse(CircularReservoir(config.days)))

  override def onEvent(state: CircularReservoir, scope: Tracker.Scope, event: Event): Option[CircularReservoir] =
    event match {
      case e: InteractionEvent if scope.matches(e) =>
        Some(state.increment(e.timestamp))
      case _ =>
        None
    }

  override def writeState(scope: Tracker.Scope, store: Store, state: CircularReservoir): IO[Unit] =
    store.save[CircularReservoir](name, scope, state)

}
