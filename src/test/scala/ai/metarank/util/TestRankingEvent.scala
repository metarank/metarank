package ai.metarank.util

import ai.metarank.model.Event.{RankItem, RankingEvent}
import ai.metarank.model.{Event, EventId, Timestamp}
import ai.metarank.model.Identifier._
import cats.data.NonEmptyList

import java.util.UUID

object TestRankingEvent {
  def apply(items: List[String]) = RankingEvent(
    id = EventId(UUID.randomUUID().toString),
    timestamp = Timestamp.now,
    user = Some(UserId("u1")),
    session = Some(SessionId("s1")),
    fields = Nil,
    items = NonEmptyList.fromListUnsafe(items).map(item => RankItem(ItemId(item), 1.0))
  )
  def event(items: List[String]): Event = apply(items)
}
