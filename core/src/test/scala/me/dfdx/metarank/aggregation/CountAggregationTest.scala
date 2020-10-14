package me.dfdx.metarank.aggregation

import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope}
import me.dfdx.metarank.model.{Featurespace, ItemId, TestClickEvent, TestRankEvent}
import me.dfdx.metarank.store.HeapStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CountAggregationTest extends AnyFlatSpec with Matchers {
  it should "apply count aggregations" in {
    val store = new HeapStore(Featurespace("p1"))
    val event = TestClickEvent(ItemId("p1"))
    val agg   = CountAggregation(store, 100)
    agg.onEvent(event).unsafeRunSync()
    val reservoir = store.value(agg.reservoir, GlobalScope(ClickType)).get().unsafeRunSync()
    reservoir.map(_.buffer.contains(1)) shouldBe Some(true)
  }
}
