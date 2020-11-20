package me.dfdx.metarank.aggregation

import me.dfdx.metarank.aggregation.Scope.EventType
import me.dfdx.metarank.model.Event.RankEvent
import me.dfdx.metarank.model.{Context, Featurespace, ItemId}

sealed trait Scope {
  def tpe: EventType
}

object Scope {
  sealed trait EventType
  case object RankType       extends EventType
  case object ClickType      extends EventType
  case object ConversionType extends EventType
  case object ItemType       extends EventType

  case class GlobalScope(tpe: EventType)                                  extends Scope
  case class ContextScope(tpe: EventType, ctx: Context)                   extends Scope
  case class ItemScope(tpe: EventType, item: ItemId)                      extends Scope
  case class ItemContextScope(tpe: EventType, item: ItemId, ctx: Context) extends Scope

  // probably we need a proper typeclass here
  // but this one is shorter :)
  def write(fs: Featurespace, scope: Scope): String = {
    val tpe = write(scope.tpe)
    scope match {
      case GlobalScope(_) =>
        s"$fs/g:$tpe"
      case ContextScope(_, ctx) =>
        s"$fs/ctx:$tpe:tag=${ctx.tag.getOrElse("")},q=${ctx.query.getOrElse("")}"
      case ItemScope(_, item) =>
        s"$fs/item:$tpe:${item.id}"
      case ItemContextScope(_, item, ctx) =>
        s"$fs/itemctx:$tpe:${item.id}:tag=${ctx.tag.getOrElse("")},q=${ctx.query.getOrElse("")}"
    }
  }

  def write(tpe: EventType): String = tpe match {
    case RankType       => "rank"
    case ClickType      => "click"
    case ConversionType => "conv"
    case ItemType       => "item"
  }
}
