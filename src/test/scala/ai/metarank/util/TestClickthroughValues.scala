package ai.metarank.util

import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{ClickthroughValues, ItemValue}

import scala.util.Random

object TestClickthroughValues {
  def apply() = ClickthroughValues(
    ct = TestClickthrough(List("p1", "p2"), List("p1")),
    values = List(
      ItemValue(ItemId("p1"), List(SingleValue(FeatureName("foo"), 1.0))),
      ItemValue(ItemId("p2"), List(SingleValue(FeatureName("foo"), 0.0)))
    )
  )

  def apply(items: List[String]) = ClickthroughValues(
    ct = TestClickthrough(items, items.take(1)),
    values = items.map(item => ItemValue(ItemId(item), List(SingleValue(FeatureName("foo"), 1.0))))
  )

  def random(items: List[String]) = {
    val shuffled = Random.shuffle(items)
    ClickthroughValues(
      ct = TestClickthrough(items, shuffled.take(1)),
      values = shuffled.map(item => ItemValue(ItemId(item), List(SingleValue(FeatureName("foo"), Random.nextDouble()))))
    )
  }
}
