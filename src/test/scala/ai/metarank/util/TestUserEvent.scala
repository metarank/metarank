package ai.metarank.util

import ai.metarank.model.Event.UserEvent
import ai.metarank.model.{EventId, Field, Timestamp}
import ai.metarank.model.Identifier.UserId

import java.util.UUID

object TestUserEvent {
  def apply(id: String, fields: List[Field] = Nil) = UserEvent(
    id = EventId(UUID.randomUUID().toString),
    user = UserId(id),
    timestamp = Timestamp.now,
    fields = fields
  )

}
