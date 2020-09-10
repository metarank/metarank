package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.config.Config.InteractionType
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.model.{Event, Timestamp}
import me.dfdx.metarank.state.CircularReservoir
import me.dfdx.metarank.store.Store

case class WindowCountingFeature(
    windows: List[Int],
    interactionType: InteractionType
) extends Feature[CircularReservoir] {

  val key = s"window_count:${interactionType.value}"

  override def readState(store: Store): IO[CircularReservoir] =
    store.load[CircularReservoir](key).map(_.getOrElse(CircularReservoir(windows.max + 1)))

  override def onEvent(state: CircularReservoir, event: Event): Option[CircularReservoir] = event match {
    case InteractionEvent(_, ts, _, _, tpe, _, _, _) if tpe == interactionType =>
      Some(state.increment(ts))
    case _ =>
      None
  }

  override def writeState(store: Store, state: CircularReservoir): IO[Unit] =
    store.save[CircularReservoir](key, state)

  override def values(state: CircularReservoir): Array[Float] = windows.map(w => state.sumLast(w).toFloat).toArray
}
