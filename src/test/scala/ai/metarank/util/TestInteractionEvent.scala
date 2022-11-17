package ai.metarank.util

import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.{EventId, Field, Timestamp}
import ai.metarank.model.Identifier._

import java.util.UUID

object TestInteractionEvent {
  def apply(item: String, parent: String, fields: List[Field] = Nil) = InteractionEvent(
    id = EventId(UUID.randomUUID().toString),
    timestamp = Timestamp.now,
    user = Some(UserId("u1")),
    session = Some(SessionId("s1")),
    fields = fields,
    item = ItemId(item),
    ranking = Some(EventId(parent)),
    `type` = "click"
  )
}
