package me.dfdx.metarank.aggregation

import me.dfdx.metarank.aggregation.ItemMetadataAggregation.ItemMetadata
import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope, ItemType}
import me.dfdx.metarank.model.{Featurespace, ItemId, TestClickEvent, TestItemMetadataEvent}
import me.dfdx.metarank.store.HeapStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ItemMetadataAggregationTest extends AnyFlatSpec with Matchers {
  it should "save product fields" in {
    val store = new HeapStore(Featurespace("p1"))
    val event = TestItemMetadataEvent("p1", "whatever")
    val agg   = ItemMetadataAggregation(store)
    agg.onEvent(event).unsafeRunSync()
    val value = store.kv(agg.feed, GlobalScope(ItemType)).get(ItemId("p1")).unsafeRunSync()
    value shouldBe Some(ItemMetadata(event.fields.toList))
  }
}
