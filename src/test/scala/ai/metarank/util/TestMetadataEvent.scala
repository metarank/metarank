package ai.metarank.util

import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.{EventId, Field, ItemId}
import io.findify.featury.model.Timestamp

import java.util.UUID

object TestMetadataEvent {
  def apply(id: String, fields: List[Field] = Nil) = ItemEvent(
    id = EventId(UUID.randomUUID().toString),
    item = ItemId(id),
    timestamp = Timestamp.now,
    fields = fields,
    tenant = "default"
  )
}
