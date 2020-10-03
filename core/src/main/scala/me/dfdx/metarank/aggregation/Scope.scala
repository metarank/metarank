package me.dfdx.metarank.aggregation

import me.dfdx.metarank.model.Event.RankEvent
import me.dfdx.metarank.model.{Context, ItemId}

sealed trait Scope

object Scope {
  sealed trait EventType
  case object RankType       extends EventType
  case object ClickType      extends EventType
  case object ConversionType extends EventType

  case class GlobalScope(tpe: EventType)                                  extends Scope
  case class ContextScope(tpe: EventType, ctx: Context)                   extends Scope
  case class ItemScope(tpe: EventType, item: ItemId)                      extends Scope
  case class ItemContextScope(tpe: EventType, item: ItemId, ctx: Context) extends Scope
}
