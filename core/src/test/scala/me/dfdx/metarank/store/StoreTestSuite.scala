package me.dfdx.metarank.store

import me.dfdx.metarank.aggregation.Scope.{ClickType, GlobalScope}
import me.dfdx.metarank.aggregation.{CircularReservoir, CountAggregation}
import me.dfdx.metarank.model.{Featurespace, Timestamp}
import me.dfdx.metarank.store.state.StateDescriptor.{MapStateDescriptor, ValueStateDescriptor}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

trait StoreTestSuite extends AnyFlatSpec with Matchers {
  lazy val store: Store = makeStore(Featurespace("default"))
  def makeStore(fs: Featurespace): Store

  val scope      = GlobalScope(ClickType)
  val valueState = ValueStateDescriptor[CircularReservoir]("count")
  val mapState   = MapStateDescriptor[String, String]("whatever")
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

  it should "not clash between multiple featurespaces" in {
    val str = ValueStateDescriptor[String]("count")
    val a   = makeStore(Featurespace("one"))
    a.value(str, scope).put("foo").unsafeRunSync()
    a.value(str, scope).get().unsafeRunSync() shouldBe Some("foo")
    val b = makeStore(Featurespace("second"))
    b.value(str, scope).get().unsafeRunSync() shouldBe None
  }

}
