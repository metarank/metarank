package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.Aggregation.EventTypeScope
import me.dfdx.metarank.aggregation.CountAggregation
import me.dfdx.metarank.aggregation.state.CircularReservoir
import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Timestamp
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait StoreTestSuite extends AnyFlatSpec with Matchers {
  def store: Store

  val k     = EventTypeScope(EventType("pageview"))
  val count = CountAggregation(10)
  val state = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))

  it should "miss non-existing items" in {
    val read = store.load[CircularReservoir](count, k).unsafeRunSync()
    read shouldBe None
  }

  it should "write/read elements" in {
    store.save(count, k, state).unsafeRunSync()
    val read = store.load[CircularReservoir](count, k).unsafeRunSync()
    read shouldBe Some(state)
  }

  it should "update existing elements" in {
    val altered = state.increment(Timestamp.day(3))
    store.save(count, k, altered).unsafeRunSync()
    val read = store.load[CircularReservoir](count, k).unsafeRunSync()
    read shouldBe Some(altered)
  }

}
