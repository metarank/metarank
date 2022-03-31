package ai.metarank.util

import ai.metarank.model.Event.{ItemRelevancy, RankingEvent}
import ai.metarank.model.{EventId, ItemId, SessionId, UserId}
import cats.data.NonEmptyList
import io.findify.featury.model.Timestamp

import java.util.UUID

object TestRankingEvent {
  def apply(items: List[String]) = RankingEvent(
    id = EventId(UUID.randomUUID().toString),
    timestamp = Timestamp.now,
    user = UserId("u1"),
    session = SessionId("s1"),
    fields = Nil,
    items = NonEmptyList.fromListUnsafe(items).map(item => ItemRelevancy(ItemId(item), 1.0)),
    tenant = "default"
  )
}
