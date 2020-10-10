package me.dfdx.metarank.model

import cats.data.NonEmptyList
import me.dfdx.metarank.model.Event.{RankEvent, RankItem}

object TestRankEvent {
  def apply(item: ItemId) = RankEvent(
    id = RequestId("r1"),
    items = NonEmptyList.one(RankItem(item, 1.0)),
    context = Context(query = Some("foo"), tag = None),
    metadata = TestMetadata()
  )
}
