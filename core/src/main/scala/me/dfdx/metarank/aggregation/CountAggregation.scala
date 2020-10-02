package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.StateDescriptor.ValueStateDescriptor

/**
  * A global counter of events. It counts searches, clicks and purchases in the following scopes:
  * - searches: globally and per scope
  * - clicks: globally, per scope, per scope+item and per item
  * - conversions: globally, per scope, per scope+item and per item
  * @param windowSize
  */
case class CountAggregation(windowSize: Int) extends Aggregation {
  override def onEvent(
      store: Store,
      scope: Aggregation.Scope,
      event: Event
  ) = for {
    stateOption <- store.value(CountAggregation.reservoir, scope).get()
    state = stateOption.getOrElse(CircularReservoir(windowSize))
    _ <- event match {
      case e: InteractionEvent if scope.matches(e) =>
        store.value(CountAggregation.reservoir, scope).put(state.increment(e.timestamp))
      case _ =>
        IO.unit
    }
  } yield {}
}

object CountAggregation {
  val reservoir = ValueStateDescriptor[CircularReservoir]("count")
}
