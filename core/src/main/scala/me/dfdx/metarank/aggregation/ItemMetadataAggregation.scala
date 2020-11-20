package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.aggregation.ItemMetadataAggregation.ItemMetadata
import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope, ItemType}
import me.dfdx.metarank.model.Event.ItemMetadataEvent
import me.dfdx.metarank.model.{Event, Field, ItemId}
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}
import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}
import shapeless.syntax.typeable._

case class ItemMetadataAggregation(store: Store) extends Aggregation {
  implicit val itemKC    = KeyCodec.gen[ItemId]
  implicit val metaCodec = Codec.gen[ItemMetadata]
  val feed               = MapStateDescriptor[ItemId, ItemMetadata]("items", ItemMetadata(Nil))
  def onEvent(event: Event): IO[Unit] = {
    event match {
      case meta: ItemMetadataEvent =>
        for {
          _ <- store.kv(feed, GlobalScope(ItemType)).put(meta.item, ItemMetadata(meta.fields.toList))
        } yield {}
    }
  }
}

object ItemMetadataAggregation {
  case class ItemMetadata(fields: List[Field])
}
