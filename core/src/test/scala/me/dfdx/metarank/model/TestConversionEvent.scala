package me.dfdx.metarank.model

import cats.data.NonEmptyList
import me.dfdx.metarank.model.Event.ConversionEvent

object TestConversionEvent {
  def apply(item: ItemId) = ConversionEvent(
    id = RequestId("r1"),
    items = NonEmptyList.one(item),
    context = Context(query = Some("foo"), tag = None),
    metadata = TestMetadata()
  )
}
