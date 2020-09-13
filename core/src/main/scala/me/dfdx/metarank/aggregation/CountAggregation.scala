package me.dfdx.metarank.aggregation

import cats.effect.IO
import cats.implicits._
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.aggregation.state.CircularReservoir
import me.dfdx.metarank.config.FeatureConfig
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig

case class CountAggregation(windowSize: Int) extends Aggregation {
  override val name = "count"

  override def onEvent(
      store: Store,
      scope: Aggregation.Scope,
      event: Event
  ) = for {
    stateOption <- store.load[CircularReservoir](this, scope)
    state = stateOption.getOrElse(CircularReservoir(windowSize))
    _ <- event match {
      case e: InteractionEvent if scope.matches(e) =>
        store.save(this, scope, state.increment(e.timestamp))
      case _ =>
        IO.unit
    }
  } yield {}

}

object CountAggregation
