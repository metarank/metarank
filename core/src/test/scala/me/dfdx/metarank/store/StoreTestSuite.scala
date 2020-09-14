package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.Aggregation.EventTypeScope
import me.dfdx.metarank.aggregation.{CircularReservoir, CountAggregation}
import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.store.state.State.{MapState, ValueState}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import me.dfdx.metarank.store.state.codecs._

trait StoreTestSuite extends AnyFlatSpec with Matchers {
  def store: Store

  val k          = EventTypeScope(EventType("pageview"))
  val valueState = ValueState[CircularReservoir]("count")
  val mapState   = MapState[String, String]("whatever")
  val state      = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))

  "value state" should "miss non-existing items" in {
    val read = store.get[CircularReservoir](valueState, k).unsafeRunSync()
    read shouldBe None
  }

  it should "write/read elements" in {
    store.put(valueState, k, state).unsafeRunSync()
    val read = store.get[CircularReservoir](valueState, k).unsafeRunSync()
    read shouldBe Some(state)
  }

  it should "update existing elements" in {
    val altered = state.increment(Timestamp.day(3))
    store.put(valueState, k, altered).unsafeRunSync()
    val read = store.get[CircularReservoir](valueState, k).unsafeRunSync()
    read shouldBe Some(altered)
  }

  "map state" should "write/read keys" in {
    store.put(mapState, k, "foo", "bar").unsafeRunSync()
    val read = store.get(mapState, k, "foo").unsafeRunSync()
    read shouldBe Some("bar")
  }

  it should "update elements" in {
    store.put(mapState, k, "foo", "baz").unsafeRunSync()
    val read = store.get(mapState, k, "foo").unsafeRunSync()
    read shouldBe Some("baz")
  }

}
