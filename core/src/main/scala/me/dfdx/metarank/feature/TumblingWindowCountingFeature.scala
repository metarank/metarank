package me.dfdx.metarank.feature

import java.io.{DataInput, DataOutput}

import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.model.{Event, Timestamp}

case class TumblingWindowCountingFeature(
    updated: Timestamp,
    saved: Timestamp,
    windows: List[Int],
    buffer: CircularReservoir,
    interactionType: String
) extends Feature {

  override def onEvent(event: Event): TumblingWindowCountingFeature =
    event match {
      case InteractionEvent(_, ts, _, _, tpe, _, _, _) if tpe == interactionType =>
        copy(buffer = buffer.increment(ts.day), updated = ts)
      case _ =>
        this
    }

  override def size: Int            = windows.size
  override def values: Array[Float] = windows.map(w => buffer.sumLast(w).toFloat).toArray
}

object TumblingWindowCountingFeature extends Feature.Loader {

  override val name = "tumbling_count"

  def apply(windows: List[Int], windowSizeDays: Int, interactionType: String) =
    new TumblingWindowCountingFeature(
      Timestamp(0),
      Timestamp(0),
      windows,
      CircularReservoir(windowSizeDays),
      interactionType
    )
}
