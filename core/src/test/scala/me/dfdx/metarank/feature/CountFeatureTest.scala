package me.dfdx.metarank.feature

import me.dfdx.metarank.aggregation.Aggregation.EventTypeScope
import me.dfdx.metarank.aggregation.CountAggregation
import me.dfdx.metarank.config.Config.{EventType, WindowConfig}
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig
import me.dfdx.metarank.model.Event.InteractionEvent
import me.dfdx.metarank.model.{Nel, RandomInteractionEvent, Timestamp}
import me.dfdx.metarank.store.HeapBytesStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CountFeatureTest extends AnyFlatSpec with Matchers {
  it should "extract counts" in {
    val store = new HeapBytesStore()
    val scope = EventTypeScope(EventType("click"))
    val event = RandomInteractionEvent(ts = Timestamp.day(0))
    val agg   = CountAggregation(100)
    agg.onEvent(store, scope, event).unsafeRunSync()
    agg.onEvent(store, scope, event.copy(timestamp = Timestamp.day(1))).unsafeRunSync()
    agg.onEvent(store, scope, event.copy(timestamp = Timestamp.day(2))).unsafeRunSync()
    agg.onEvent(store, scope, event.copy(timestamp = Timestamp.day(3))).unsafeRunSync()
    val result = CountFeature(agg, CountFeatureConfig(Nel(WindowConfig(1, 10))))
      .values(scope, store)
      .unsafeRunSync()
    result shouldBe List(3.0f) // the last one is not counted as we exclude current day
  }
}
