package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{FeatureValue, Key, Timestamp}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.ItemScope
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FileKVStoreTest extends AnyFlatSpec with Matchers with FileTest {
  val now = Timestamp.now
  it should "pull state" in {
    val f   = FileKVStore(db, "a", BinaryStoreFormat)
    val key = Key(ItemScope(ItemId("p1")), FeatureName("a"))
    f.put(Map(key -> ScalarValue(key, now, SString("foo")))).unsafeRunSync()
    val state = FileKVStore.kvStateSource.source(f).compile.toList.unsafeRunSync()
    state shouldBe List(ScalarValue(key, now, SString("foo")))
  }
}
