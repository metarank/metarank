package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.{Context, Event, ItemId}
import me.dfdx.metarank.store.Store

trait Aggregation {
  def onEvent(store: Store, scope: Aggregation.Scope, event: Event): IO[Unit]
}

object Aggregation {
  sealed trait Scope {
    def key: String
  }

  case object GlobalScope extends Scope {
    val key = "g"
  }

  case class ContextScope(ctx: Context) extends Scope {
    val key = (ctx.query, ctx.tag) match {
      case (Some(q), None) => s"c:$q"
      case (_, Some(tag))  => s"c:$tag"
    }
  }

  sealed trait InteractionScope extends Scope

  case object RankScope extends InteractionScope {
    val key = "t:rank"
  }

  case object ClickScope extends InteractionScope {
    val key = "t:click"
  }

  case class ItemScope(id: ItemId) extends Scope {
    def key = s"i:${id.id}"
  }
}
