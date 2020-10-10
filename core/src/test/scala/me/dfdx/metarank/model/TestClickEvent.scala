package me.dfdx.metarank.model

import me.dfdx.metarank.model.Event.{ClickEvent, Metadata}

object TestClickEvent {
  def apply(item: ItemId = ItemId("p1")) = new ClickEvent(
    id = RequestId("r1"),
    item = item,
    context = Context(Some("foo"), None),
    metadata = Metadata(
      timestamp = Timestamp(1L),
      user = UserId("u1"),
      session = SessionId("s1"),
      ua = None,
      ip = None
    )
  )
}
