package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.StateDescriptor.ValueStateDescriptor

case class CountAggregation(windowSize: Int) extends Aggregation {
  val reservoir = ValueStateDescriptor[CircularReservoir]("count")
  override def onEvent(
      store: Store,
      scope: Aggregation.Scope,
      event: Event
  ) = for {
    stateOption <- store.value(reservoir, scope).get()
    state = stateOption.getOrElse(CircularReservoir(windowSize))
    _ <- event match {
      case e: InteractionEvent if scope.matches(e) =>
        store.value(reservoir, scope).put(state.increment(e.timestamp))
      case _ =>
        IO.unit
    }
  } yield {}

}
