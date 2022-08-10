package ai.metarank.flow

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.{Env, FeatureValue, Key, Timestamp}
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.ItemScope
import ai.metarank.util.TestFeatureMapping
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FeatureValueSinkTest extends AnyFlatSpec with Matchers {
  it should "write values to store" in {
    val store = MemPersistence(TestFeatureMapping().schema)
    val key   = Key(ItemScope(Env.default, ItemId("p1")), FeatureName("foo"))
    val value = ScalarValue(key, Timestamp.now, SString("bar"))
    fs2.Stream
      .emit[IO, FeatureValue](value)
      .through(FeatureValueSink(store).write)
      .compile
      .drain
      .unsafeRunSync()
    store.values.get(List(key)).unsafeRunSync() shouldBe Map(key -> value)
  }
}
