package me.dfdx.metarank.store

import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.aggregation.Aggregation.EventTypeScope
import me.dfdx.metarank.aggregation.CountAggregation
import me.dfdx.metarank.aggregation.state.CircularReservoir
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HeapBytesStoreTest extends AnyFlatSpec with Matchers {
  it should "save/load reservoirs" in {
    val key   = EventTypeScope(EventType("pageview"))
    val count = CountAggregation(10)
    val state = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))
    val store = new HeapBytesStore()
    store.save(count, key, state).unsafeRunSync()
    val read = store.load[CircularReservoir](count, key).unsafeRunSync()
    read shouldBe Some(state)
  }
}
