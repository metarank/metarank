package ai.metarank.util

import ai.metarank.model.Event.MetadataEvent
import ai.metarank.model.{EventId, Field, ItemId}
import io.findify.featury.model.Timestamp

import java.util.UUID

object TestMetadataEvent {
  def apply(id: String, fields: List[Field] = Nil) = MetadataEvent(
    id = EventId(UUID.randomUUID().toString),
    item = ItemId(id),
    timestamp = Timestamp.now,
    fields = fields,
    tenant = Some("default")
  )
}
