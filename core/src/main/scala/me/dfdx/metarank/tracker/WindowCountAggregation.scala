package me.dfdx.metarank.tracker

import cats.effect.IO
import cats.implicits._
import me.dfdx.metarank.config.Config.FeatureConfig
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.state.CircularReservoir

object WindowCountAggregation extends Aggregation {
  override val name = "window_count"

  override def onEvent(
      features: List[FeatureConfig],
      store: Store,
      scope: Aggregation.Scope,
      event: Event
  ) = for {
    stateOption <- store.load[CircularReservoir](this, scope)
    state = stateOption.getOrElse(CircularReservoir(features.map(_.maxDays).max))
    _ <- event match {
      case e: InteractionEvent if scope.matches(e) =>
        store.save(this, scope, state.increment(e.timestamp))
      case _ =>
        IO.unit
    }
  } yield {}

}
