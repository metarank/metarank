package me.dfdx.metarank.feature

import me.dfdx.metarank.aggregation.CountAggregation
import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope}
import me.dfdx.metarank.config.Config.WindowConfig
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig
import me.dfdx.metarank.model.{ItemId, Nel, TestClickEvent, TestRankEvent, Timestamp}
import me.dfdx.metarank.store.{HeapBytesStore, HeapStore}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CountFeatureTest extends AnyFlatSpec with Matchers {
  it should "extract counts" in {
    val store = new HeapStore()
    val event = TestClickEvent(ItemId("p1"))
    val rank  = TestRankEvent(ItemId("p1"))
    val agg   = CountAggregation(store, 100)
    agg.onEvent(event).unsafeRunSync()
    agg.onEvent(event.copy(metadata = event.metadata.copy(timestamp = Timestamp.day(1)))).unsafeRunSync()
    agg.onEvent(event.copy(metadata = event.metadata.copy(timestamp = Timestamp.day(2)))).unsafeRunSync()
    agg.onEvent(event.copy(metadata = event.metadata.copy(timestamp = Timestamp.day(3)))).unsafeRunSync()
    val result = CountFeature(agg, CountFeatureConfig(Nel(WindowConfig(1, 10))))
      .values(rank, rank.items.head)
      .unsafeRunSync()
    result shouldBe List(3.0f, 3.0f, 0f, 0f) // the last one is not counted as we exclude current day
  }
}
