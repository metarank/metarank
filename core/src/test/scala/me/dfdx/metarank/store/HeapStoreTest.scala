package me.dfdx.metarank.store

import me.dfdx.metarank.config.Config.InteractionType
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.tracker.Tracker.InteractionTypeScope
import me.dfdx.metarank.tracker.state.CircularReservoir
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HeapStoreTest extends AnyFlatSpec with Matchers {
  it should "save/load reservoirs" in {
    val key   = InteractionTypeScope(InteractionType("foo"))
    val state = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))
    val store = new HeapStore()
    store.save("foo", key, state).unsafeRunSync()
    val read = store.load[CircularReservoir]("foo", key).unsafeRunSync()
    read shouldBe Some(state)
  }
}
