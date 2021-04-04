package me.dfdx.metarank.aggregation

import me.dfdx.metarank.aggregation.ItemMetadataAggregation.ItemMetadata
import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope, ItemType, RankType}
import me.dfdx.metarank.config.Config.FieldType.{NumericType, StringType}
import me.dfdx.metarank.config.Config.{FieldConfig, FieldFormatConfig, SchemaConfig}
import me.dfdx.metarank.model.{Featurespace, ItemId, Nel, TestClickEvent, TestItemMetadataEvent}
import me.dfdx.metarank.store.HeapStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import scala.util.Try

class ItemMetadataAggregationTest extends AnyFlatSpec with Matchers {
  it should "save product fields" in {
    val store  = new HeapStore(Featurespace("p1"))
    val event  = TestItemMetadataEvent("p1", "whatever")
    val schema = SchemaConfig(Nel(FieldConfig("title", FieldFormatConfig(StringType))))
    val agg    = ItemMetadataAggregation(store, schema)
    agg.onEvent(event).unsafeRunSync()
    val value = store.kv(agg.feed, GlobalScope(RankType)).get(ItemId("p1")).unsafeRunSync()
    value shouldBe Some(ItemMetadata(event.fields))
  }

  it should "reject items with schema mismatch: non-existing field" in {
    val store  = new HeapStore(Featurespace("p1"))
    val event  = TestItemMetadataEvent("p1", "whatever")
    val schema = SchemaConfig(Nel(FieldConfig("nope", FieldFormatConfig(StringType))))
    val agg    = ItemMetadataAggregation(store, schema)
    Try(agg.onEvent(event).unsafeRunSync()).isFailure shouldBe true
  }

  it should "reject items with schema mismatch: type mismatch" in {
    val store  = new HeapStore(Featurespace("p1"))
    val event  = TestItemMetadataEvent("p1", "whatever")
    val schema = SchemaConfig(Nel(FieldConfig("title", FieldFormatConfig(NumericType))))
    val agg    = ItemMetadataAggregation(store, schema)
    Try(agg.onEvent(event).unsafeRunSync()).isFailure shouldBe true
  }
}
