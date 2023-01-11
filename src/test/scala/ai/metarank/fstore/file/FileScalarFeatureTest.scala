package ai.metarank.fstore.file

import ai.metarank.fstore.ScalarFeatureSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.State.ScalarState
import ai.metarank.model.Write.Put
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class FileScalarFeatureTest extends ScalarFeatureSuite with FileTest {
  override def feature(config: ScalarConfig): FileScalarFeature = FileScalarFeature(config, db, "x", BinaryStoreFormat)

  it should "pull state" in {
    val c = config.copy(name = FeatureName("ss"))
    val f = feature(c)
    f.put(Put(TestKey(c, "a"), now, SString("foo"))).unsafeRunSync()

    val all = FileScalarFeature.fileScalarSource.source(f).compile.toList.unsafeRunSync()
    all shouldBe List(
      ScalarState(TestKey(c, "a"), SString("foo"))
    )
  }

}
