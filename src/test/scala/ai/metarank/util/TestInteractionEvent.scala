package ai.metarank.util

import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy}
import ai.metarank.model.{EventId, Field}
import ai.metarank.model.Identifier._
import io.findify.featury.model.Timestamp

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
    `type` = "click",
    tenant = "default"
  )
}
