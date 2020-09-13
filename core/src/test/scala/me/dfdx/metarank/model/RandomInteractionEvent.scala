package me.dfdx.metarank.model

import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Event.InteractionEvent

object RandomInteractionEvent {
  def apply(id: String = "1", ts: Timestamp = Timestamp(0)) = InteractionEvent(
    id = RequestId(id),
    timestamp = ts,
    user = UserId("u1"),
    session = SessionId("s1"),
    `type` = EventType("click"),
    item = ItemId("p1")
  )
}
