package ai.metarank.util

import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{EventId, Field, Timestamp}

import java.util.UUID

object TestItemEvent {
  def apply(id: String, fields: List[Field] = Nil) = ItemEvent(
    id = EventId(UUID.randomUUID().toString),
    item = ItemId(id),
    timestamp = Timestamp.now,
    fields = fields
  )
}
