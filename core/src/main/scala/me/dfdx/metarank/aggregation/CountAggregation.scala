package me.dfdx.metarank.aggregation

import cats.effect.IO
import cats.implicits._
import me.dfdx.metarank.model.Event.{ClickEvent, ConversionEvent, RankEvent}
import me.dfdx.metarank.model.{Event, Nel, Timestamp}
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.StateDescriptor.ValueStateDescriptor

/** A global counter of events. It counts searches, clicks and purchases in the following scopes:
  * - searches: globally and per scope
  * - clicks: globally, per scope, per scope+item and per item
  * - conversions: globally, per scope, per scope+item and per item
  * @param windowSize
  */
case class CountAggregation(store: Store, windowSize: Int) extends Aggregation {
  val reservoir = ValueStateDescriptor("count", CircularReservoir(windowSize))

  override def onEvent(event: Event): IO[Unit] = {
    event match {
      case rank: RankEvent       => increment(rank.scopes, rank.metadata.timestamp)
      case click: ClickEvent     => increment(click.scopes, click.metadata.timestamp)
      case conv: ConversionEvent => increment(conv.scopes, conv.metadata.timestamp)
      case _                     => IO.unit
    }
  }

  def increment(scopes: List[Scope], ts: Timestamp): IO[Unit] = {
    scopes.map(increment(_, ts)).sequence.map(_ => ())
  }

  def increment(scope: Scope, ts: Timestamp): IO[Unit] = {
    val valueState = store.value(reservoir, scope)
    for {
      state <- valueState.get().map(_.getOrElse(reservoir.default))
      _     <- valueState.put(state.increment(ts))
    } yield {}
  }
}
