package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.semiauto._

sealed trait Event {
  def timestamp: Timestamp
}

object Event {
  case class RankItem(id: ItemId, relevancy: Double)

  case class ItemMetadataEvent(id: ItemId, timestamp: Timestamp, fields: NonEmptyList[Field])    extends Event
  case class RankEvent(id: RequestId, timestamp: Timestamp, items: NonEmptyList[RankItem])       extends Event
  case class InteractionEvent(id: RequestId, timestamp: Timestamp, `type`: String, item: ItemId) extends Event

  implicit val rankItemCodec     = deriveCodec[RankItem]
  implicit val itemMetadataCodec = deriveCodec[ItemMetadataEvent]
  implicit val rankEventCodec    = deriveCodec[RankEvent]
  implicit val interactionCodec  = deriveCodec[InteractionEvent]
}
