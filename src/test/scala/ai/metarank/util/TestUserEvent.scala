package ai.metarank.util

import ai.metarank.model.Event.{ItemEvent, UserEvent}
import ai.metarank.model.{EventId, Field}
import ai.metarank.model.Identifier.{ItemId, UserId}
import io.findify.featury.model.Timestamp

import java.util.UUID

object TestUserEvent {
  def apply(id: String, fields: List[Field] = Nil) = UserEvent(
    id = EventId(UUID.randomUUID().toString),
    user = UserId(id),
    timestamp = Timestamp.now,
    fields = fields,
    env = "default"
  )

}
