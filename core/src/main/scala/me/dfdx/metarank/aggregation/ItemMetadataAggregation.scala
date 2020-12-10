package me.dfdx.metarank.aggregation

import cats.data.NonEmptyList
import cats.effect.IO
import me.dfdx.metarank.aggregation.ItemMetadataAggregation.ItemMetadata
import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope, ItemType, RankType}
import me.dfdx.metarank.config.Config.FieldType.{BooleanType, NumericType, StringType}
import me.dfdx.metarank.config.Config.SchemaConfig
import me.dfdx.metarank.model.Event.ItemMetadataEvent
import me.dfdx.metarank.model.{Event, Field, ItemId, SchemaMismatchError}
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}
import me.dfdx.metarank.store.state.codec.{Codec, KeyCodec}
import shapeless.syntax.typeable._

case class ItemMetadataAggregation(store: Store, schema: SchemaConfig) extends Aggregation {
  implicit val itemKC    = KeyCodec.gen[ItemId]
  implicit val metaCodec = Codec.gen[ItemMetadata]
  val feed               = MapStateDescriptor[ItemId, ItemMetadata]("items")
  def onEvent(event: Event): IO[Unit] = {
    event match {
      case meta: ItemMetadataEvent =>
        for {
          _ <- validateEvent(meta)
          _ <- store.kv(feed, GlobalScope(RankType)).put(meta.item, ItemMetadata(meta.fields))
        } yield {}
    }
  }

  def validateEvent(event: ItemMetadataEvent): IO[Unit] = {
    event.fields.find(field => !schema.fieldExists(field.name)) match {
      case Some(notFound) =>
        IO.raiseError(SchemaMismatchError(notFound.name, "field not found in schema"))
      case None =>
        event.fields.find(field => !schema.fieldTypeValid(field)) match {
          case Some(typeMismatch) =>
            IO.raiseError(
              SchemaMismatchError(typeMismatch.name, s"field type ${typeMismatch.tpe.name} is not matching schema")
            )
          case None =>
            IO.unit
        }
    }
  }

}

object ItemMetadataAggregation {
  case class ItemMetadata(fields: NonEmptyList[Field])
}
