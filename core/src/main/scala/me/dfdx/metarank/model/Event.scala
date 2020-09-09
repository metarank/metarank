package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.semiauto._

sealed trait Event {
  def timestamp: Timestamp
}

object Event {
  case class RankItem(id: ItemId, relevancy: Double)

  case class ItemMetadataEvent(id: ItemId, timestamp: Timestamp, fields: NonEmptyList[Field]) extends Event
  case class RankEvent(id: RequestId, timestamp: Timestamp, items: NonEmptyList[RankItem])    extends Event
  case class InteractionEvent(
      id: RequestId,
      timestamp: Timestamp,
      user: UserId,
      session: SessionId,
      `type`: String,
      item: ItemId,
      ua: Option[UserAgent] = None,
      ip: Option[IPAddr] = None
  ) extends Event

  implicit val rankItemDecoder = deriveDecoder[RankItem]
    .ensure(!_.relevancy.isNaN, "relevancy score cannot be NaN")
    .ensure(!_.relevancy.isInfinite, "relevancy score cannot be Inf")

  implicit val rankItemEncoder = deriveEncoder[RankItem]

  implicit val itemMetadataCodec = deriveCodec[ItemMetadataEvent]
  implicit val rankEventCodec    = deriveCodec[RankEvent]
  implicit val interactionDecoder = deriveDecoder[InteractionEvent]
    .ensure(_.`type`.nonEmpty, "interaction type cannot be empty")
  implicit val interactionEncoder = deriveEncoder[InteractionEvent]
}
