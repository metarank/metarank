package me.dfdx.metarank.store

import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.tracker.Aggregation.InteractionTypeScope
import me.dfdx.metarank.tracker.WindowCountAggregation
import me.dfdx.metarank.tracker.state.CircularReservoir
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HeapStoreTest extends AnyFlatSpec with Matchers {
  it should "save/load reservoirs" in {
    val key   = InteractionTypeScope(EventType("pageview"))
    val state = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))
    val store = new HeapStore()
    store.save(WindowCountAggregation, key, state).unsafeRunSync()
    val read = store.load[CircularReservoir](WindowCountAggregation, key).unsafeRunSync()
    read shouldBe Some(state)
  }
}
