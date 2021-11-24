package ai.metarank.util

import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy}
import ai.metarank.model.{EventId, Field, ItemId, SessionId, UserId}
import io.findify.featury.model.Timestamp

import java.util.UUID

object TestInteractionEvent {
  def apply(item: String, parent: String, fields: List[Field]) = InteractionEvent(
    id = EventId(UUID.randomUUID().toString),
    timestamp = Timestamp.now,
    user = UserId("u1"),
    session = SessionId("s1"),
    fields = fields,
    item = ItemId(item),
    ranking = EventId(parent),
    `type` = "click",
    tenant = Some("default")
  )
}
