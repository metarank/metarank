package me.dfdx.metarank.model

import me.dfdx.metarank.model.Event.ItemMetadataEvent
import me.dfdx.metarank.model.Field.StringField

object TestItemMetadataEvent {
  def apply(id: String, title: String = "title") = ItemMetadataEvent(
    item = ItemId(id),
    timestamp = Timestamp(0L),
    fields = Nel(StringField("title", title))
  )
}
