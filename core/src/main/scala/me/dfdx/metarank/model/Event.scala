package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.Codec
import io.circe.generic.semiauto._
import me.dfdx.metarank.aggregation.Scope
import me.dfdx.metarank.aggregation.Scope.{
  ClickType,
  ContextScope,
  ConversionType,
  GlobalScope,
  ItemContextScope,
  ItemScope,
  RankType
}

sealed trait Event {
  def scopes: List[Scope] = Nil
}

object Event {
  case class Metadata(timestamp: Timestamp, user: UserId, session: SessionId, ua: Option[UserAgent], ip: Option[IPAddr])
  case class RankItem(id: ItemId, relevancy: Double)

  case class ItemMetadataEvent(item: ItemId, timestamp: Timestamp, fields: NonEmptyList[Field]) extends Event
  case class RankEvent(id: RequestId, items: NonEmptyList[RankItem], context: Context, metadata: Metadata)
      extends Event {
    override val scopes = List(GlobalScope(RankType), ContextScope(RankType, context))
  }
  case class ClickEvent(id: RequestId, item: ItemId, context: Context, metadata: Metadata) extends Event {
    override val scopes = List(
      GlobalScope(ClickType),
      ContextScope(ClickType, context),
      ItemScope(ClickType, item),
      ItemContextScope(ClickType, item, context)
    )
  }
  case class ConversionEvent(id: RequestId, items: NonEmptyList[ItemId], context: Context, metadata: Metadata)
      extends Event {
    override val scopes = List.concat(
      List(GlobalScope(ConversionType), ContextScope(ConversionType, context)),
      items.map(ItemScope(ConversionType, _)).toList,
      items.map(ItemContextScope.apply(ConversionType, _, context)).toList
    )
  }

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
