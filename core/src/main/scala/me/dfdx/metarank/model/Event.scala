package me.dfdx.metarank.model

import cats.data.NonEmptyList

sealed trait Event {
  def timestamp: Timestamp
}

object Event {
  case class InteractionType(value: String) extends AnyVal
  case class RankItem(id: ItemId, relevancy: Double)

  case class ItemMetadata(id: ItemId, timestamp: Timestamp, fields: NonEmptyList[Field])             extends Event
  case class RankEvent(id: RequestId, timestamp: Timestamp, items: NonEmptyList[RankItem])           extends Event
  case class Interaction(id: RequestId, timestamp: Timestamp, `type`: InteractionType, item: ItemId) extends Event
}
