package me.dfdx.metarank.tracker

import cats.effect.IO
import me.dfdx.metarank.config.Config.{EventType, FeatureConfig}
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.model.{Event, ItemId}
import me.dfdx.metarank.store.Store

trait Aggregation {
  def name: String
  def onEvent(features: List[FeatureConfig], store: Store, scope: Aggregation.Scope, event: Event): IO[Unit]
}

object Aggregation {
  sealed trait Scope {
    def key: String
    def matches(event: InteractionEvent): Boolean
  }
  case class InteractionTypeScope(tpe: EventType) extends Scope {
    def key = s"t:${tpe.value}"

    override def matches(event: InteractionEvent): Boolean = event.`type` == tpe
  }
  case class ItemScope(id: ItemId) extends Scope {
    def key = s"i:${id.id}"

    override def matches(event: InteractionEvent): Boolean = event.item == id
  }
  case class ItemAndTypeScope(tpe: EventType, id: ItemId) extends Scope {
    def key = s"t:${tpe.value}|i:${id.id}"

    override def matches(event: InteractionEvent): Boolean = (event.item == id) && (event.`type` == tpe)
  }
}
