package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.StateDescriptor.ValueStateDescriptor

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
