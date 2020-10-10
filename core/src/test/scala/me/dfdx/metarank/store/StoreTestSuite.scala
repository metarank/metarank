package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope}
import me.dfdx.metarank.aggregation.{CircularReservoir, CountAggregation}
import me.dfdx.metarank.model.Timestamp
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait StoreTestSuite extends AnyFlatSpec with Matchers {
  def store: Store

  val scope      = GlobalScope(ClickType)
  val valueState = ValueStateDescriptor[CircularReservoir]("count", CircularReservoir(10))
  val mapState   = MapStateDescriptor[String, String]("whatever", "empty")
  val state      = CircularReservoir(10).increment(Timestamp.day(1)).increment(Timestamp.day(2))

  "value state" should "miss non-existing items" in {
    val read = store.value(valueState, scope).get().unsafeRunSync()
    read shouldBe None
  }

  it should "write/read elements" in {
    store.value(valueState, scope).put(state).unsafeRunSync()
    val read = store.value(valueState, scope).get().unsafeRunSync()
    read shouldBe Some(state)
  }

  it should "update existing elements" in {
    val altered = state.increment(Timestamp.day(3))
    store.value(valueState, scope).put(altered).unsafeRunSync()
    val read = store.value(valueState, scope).get().unsafeRunSync()
    read shouldBe Some(altered)
  }

  it should "delete elements" in {
    val read1 = store.value(valueState, scope).get().unsafeRunSync()
    read1.isDefined shouldBe true
    store.value(valueState, scope).delete().unsafeRunSync()
    val read2 = store.value(valueState, scope).get().unsafeRunSync()
    read2.isDefined shouldBe false
  }

  "map state" should "write/read keys" in {
    store.kv(mapState, scope).put("foo", "bar").unsafeRunSync()
    val read = store.kv(mapState, scope).get("foo").unsafeRunSync()
    read shouldBe Some("bar")
  }

  it should "update elements" in {
    store.kv(mapState, scope).put("foo", "baz").unsafeRunSync()
    val read = store.kv(mapState, scope).get("foo").unsafeRunSync()
    read shouldBe Some("baz")
  }

  it should "delete elements" in {
    store.kv(mapState, scope).put("foo2", "bar").unsafeRunSync()
    val read = store.kv(mapState, scope).get("foo2").unsafeRunSync()
    read shouldBe Some("bar")
    store.kv(mapState, scope).delete("foo2").unsafeRunSync()
    val read2 = store.kv(mapState, scope).get("foo2").unsafeRunSync()
    read2.isDefined shouldBe false
  }

}
