package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.State.ValueState

case class CountAggregation(windowSize: Int) extends Aggregation {
  val reservoir = ValueState[CircularReservoir]("count")
  override def onEvent(
      store: Store,
      scope: Aggregation.Scope,
      event: Event
  ) = for {
    stateOption <- store.get(reservoir, scope)
    state = stateOption.getOrElse(CircularReservoir(windowSize))
    _ <- event match {
      case e: InteractionEvent if scope.matches(e) =>
        store.put(reservoir, scope, state.increment(e.timestamp))
      case _ =>
        IO.unit
    }
  } yield {}

}
