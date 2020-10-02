package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.Codec
import io.circe.generic.semiauto._

sealed trait Event

object Event {
  case class Metadata(timestamp: Timestamp, user: UserId, session: SessionId, ua: Option[UserAgent], ip: Option[IPAddr])
  case class RankItem(id: ItemId, relevancy: Double)

  case class ItemMetadataEvent(item: ItemId, timestamp: Timestamp, fields: NonEmptyList[Field]) extends Event
  case class RankEvent(id: RequestId, items: NonEmptyList[RankItem], metadata: Metadata)        extends Event
  case class ClickEvent(id: RequestId, item: ItemId, metadata: Metadata)                        extends Event
  case class ConversionEvent(id: RequestId, items: NonEmptyList[ItemId], metadata: Metadata)    extends Event

  implicit val rankItemDecoder = deriveDecoder[RankItem]
    .ensure(!_.relevancy.isNaN, "relevancy score cannot be NaN")
    .ensure(!_.relevancy.isInfinite, "relevancy score cannot be Inf")

  implicit val metaCodec       = deriveCodec[Metadata]
  implicit val rankItemEncoder = deriveEncoder[RankItem]

  implicit val itemMetadataCodec = deriveCodec[ItemMetadataEvent]
  implicit val rankEventCodec    = deriveCodec[RankEvent]
  implicit val clickCodec        = deriveCodec[ClickEvent]
  implicit val convCodec         = deriveCodec[ConversionEvent]
}
