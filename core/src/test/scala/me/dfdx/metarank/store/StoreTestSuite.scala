package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.{CircularReservoir, CountAggregation}
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait StoreTestSuite extends AnyFlatSpec with Matchers {
  def store: Store

  val k          = EventTypeScope(EventType("pageview"))
  val valueState = ValueStateDescriptor[CircularReservoir]("count")
  val mapState   = MapStateDescriptor[String, String]("whatever")
  val state      = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))

  "value state" should "miss non-existing items" in {
    val read = store.value(valueState, k).get().unsafeRunSync()
    read shouldBe None
  }

  it should "write/read elements" in {
    store.value(valueState, k).put(state).unsafeRunSync()
    val read = store.value(valueState, k).get().unsafeRunSync()
    read shouldBe Some(state)
  }

  it should "update existing elements" in {
    val altered = state.increment(Timestamp.day(3))
    store.value(valueState, k).put(altered).unsafeRunSync()
    val read = store.value(valueState, k).get().unsafeRunSync()
    read shouldBe Some(altered)
  }

  "map state" should "write/read keys" in {
    store.kv(mapState, k).put("foo", "bar").unsafeRunSync()
    val read = store.kv(mapState, k).get("foo").unsafeRunSync()
    read shouldBe Some("bar")
  }

  it should "update elements" in {
    store.kv(mapState, k).put("foo", "baz").unsafeRunSync()
    val read = store.kv(mapState, k).get("foo").unsafeRunSync()
    read shouldBe Some("baz")
  }

}
