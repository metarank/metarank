package ai.metarank.util

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import ai.metarank.model.{Clickthrough, EventId, Timestamp}

import java.util.UUID

object TestClickthrough {
  def apply(items: List[String], clicks: List[String]) = Clickthrough(
    id = EventId(UUID.randomUUID().toString),
    ts = Timestamp.now,
    user = UserId("u1"),
    session = Some(SessionId("s1")),
    items = items.map(ItemId.apply),
    interactions = clicks.map(id => TypedInteraction(ItemId(id), "click"))
  )
}
