package me.dfdx.metarank.model

import me.dfdx.metarank.model.Event.Metadata

object TestMetadata {
  def apply() = Metadata(
    timestamp = Timestamp(1L),
    user = UserId("u1"),
    session = SessionId("s1"),
    ua = None,
    ip = None
  )
}
