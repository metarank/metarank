package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.InteractionType
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.model.{Event, Timestamp}
import me.dfdx.metarank.state.CircularReservoir

case class WindowCountingFeature(
    windows: List[Int],
    interactionType: InteractionType
) extends Feature[CircularReservoir] {
  override val name = "window_count"

  override def onEvent(state: CircularReservoir, event: Event): CircularReservoir =
    event match {
      case InteractionEvent(_, ts, _, _, tpe, _, _, _) if tpe == interactionType =>
        state.increment(ts)
      case _ =>
        state
    }

  override def values(state: CircularReservoir): Array[Float] = windows.map(w => state.sumLast(w).toFloat).toArray
}
