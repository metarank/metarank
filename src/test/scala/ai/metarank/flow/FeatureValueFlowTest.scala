package ai.metarank.flow

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.NumberField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.{Env, Key}
import ai.metarank.model.Scope.ItemScope
import ai.metarank.util.{TestFeatureMapping, TestItemEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import fs2.Stream

class FeatureValueFlowTest extends AnyFlatSpec with Matchers {
  val mapping = TestFeatureMapping()
  val event   = TestItemEvent("p1").copy(fields = List(NumberField("price", 10)))

  it should "accept writes" in {
    val flow = FeatureValueFlow(mapping, MemPersistence(mapping.schema))
    val values = Stream
      .emit(event)
      .through(flow.process)
      .compile
      .toList
      .unsafeRunSync()
    values shouldBe List(
      ScalarValue(Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("price")), event.timestamp, SDouble(10.0))
    )
  }

  it should "obey refresh rate" in {
    val flow = FeatureValueFlow(mapping, MemPersistence(mapping.schema))
    val values = Stream
      .emits(List(event, event, event))
      .through(flow.process)
      .compile
      .toList
      .unsafeRunSync()
    values shouldBe List(
      ScalarValue(Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("price")), event.timestamp, SDouble(10.0))
    )
  }
}
