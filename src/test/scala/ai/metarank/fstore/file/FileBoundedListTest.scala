package ai.metarank.fstore.file

import ai.metarank.fstore.{BoundedListSuite, ScalarFeatureSuite}
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.{GlobalScope, ItemScope}
import ai.metarank.model.Write.Append
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

import scala.util.Random

class FileBoundedListTest extends BoundedListSuite with FileTest {
  override def feature(config: BoundedListConfig): FileBoundedListFeature =
    FileBoundedListFeature(config, db.sortedDB(config.name.value + Random.nextInt()), BinaryStoreFormat)

  it should "emit state" in {
    val f = feature(config.copy(name = FeatureName("fbl")))
    f.put(Append(Key(ItemScope(ItemId("p1")), f.config.name), SString("a"), now)).unsafeRunSync()
    f.put(Append(Key(ItemScope(ItemId("p2")), f.config.name), SString("b"), now)).unsafeRunSync()
    val state = FileBoundedListFeature.fileListSource.source(f).compile.toList.unsafeRunSync()
    state.map(_.key.scope) should contain theSameElementsAs List(
      ItemScope(ItemId("p1")),
      ItemScope(ItemId("p2"))
    )
  }

}
