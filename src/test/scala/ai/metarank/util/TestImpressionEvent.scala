package ai.metarank.util

import ai.metarank.model.Event.{ImpressionEvent, ItemRelevancy}
import ai.metarank.model.{EventId, ItemId, SessionId, UserId}
import io.findify.featury.model.Timestamp

import java.util.UUID

object TestImpressionEvent {
  def apply(items: List[String]) = ImpressionEvent(
    id = EventId(UUID.randomUUID().toString),
    timestamp = Timestamp.now,
    user = UserId("u1"),
    session = SessionId("s1"),
    fields = Nil,
    items = items.map(item => ItemRelevancy(ItemId(item), 1.0)),
    tenant = Some("default")
  )
}
