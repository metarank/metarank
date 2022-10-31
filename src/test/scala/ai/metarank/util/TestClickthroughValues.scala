package ai.metarank.util

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{ClickthroughValues, ItemValue}

object TestClickthroughValues {
  def apply() = ClickthroughValues(
    ct = TestClickthrough(List("p1", "p2"), List("p1")),
    values = List(
      ItemValue(ItemId("p1"), List(SingleValue(FeatureName("foo"), 1.0))),
      ItemValue(ItemId("p2"), List(SingleValue(FeatureName("foo"), 0.0)))
    )
  )
}
