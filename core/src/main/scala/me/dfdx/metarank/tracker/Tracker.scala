package me.dfdx.metarank.tracker

import cats.effect.IO
import me.dfdx.metarank.config.Config.{InteractionType, TrackerConfig}
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.model.{Event, ItemId}
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.state.State

trait Tracker[T <: State] {
  def name: String
  protected def readState(config: TrackerConfig, scope: Tracker.Scope, store: Store): IO[T]
  protected def onEvent(state: T, scope: Tracker.Scope, event: Event): Option[T]
  protected def writeState(scope: Tracker.Scope, store: Store, state: T): IO[Unit]

  def onEvent(config: TrackerConfig, scope: Tracker.Scope, store: Store, event: Event): IO[Unit] = {
    readState(config, scope, store).flatMap { state =>
      onEvent(state, scope, event) match {
        case None          => IO.unit
        case Some(updated) => writeState(scope, store, updated)
      }
    }
  }
}

object Tracker {
  sealed trait Scope {
    def key: String
    def matches(event: InteractionEvent): Boolean
  }
  case class InteractionTypeScope(tpe: InteractionType) extends Scope {
    def key = s"t:${tpe.value}"

    override def matches(event: InteractionEvent): Boolean = event.`type` == tpe
  }
  case class ItemScope(id: ItemId) extends Scope {
    def key = s"i:${id.id}"

    override def matches(event: InteractionEvent): Boolean = event.item == id
  }
  case class ItemAndTypeScope(tpe: InteractionType, id: ItemId) extends Scope {
    def key = s"t:${tpe.value}|i:${id.id}"

    override def matches(event: InteractionEvent): Boolean = (event.item == id) && (event.`type` == tpe)
  }
}
