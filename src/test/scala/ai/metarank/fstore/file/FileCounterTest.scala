package ai.metarank.fstore.file

import ai.metarank.fstore.CounterSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.State.CounterState
import ai.metarank.model.Write.Increment
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

import scala.util.Random

class FileCounterTest extends CounterSuite with FileTest {
  override def feature(config: CounterConfig): FileCounterFeature =
    FileCounterFeature(config, db.sortedIntDB(config.name.value + Random.nextInt()), BinaryStoreFormat)

  it should "pull state" in {
    val c = CounterConfig(ItemScopeType, FeatureName("c2"))
    val f = feature(c)
    f.put(Increment(TestKey(c, id = "p12"), now, 1)).unsafeRunSync()
    f.put(Increment(TestKey(c, id = "p13"), now, 2)).unsafeRunSync()
    val state = FileCounterFeature.counterSource.source(f).compile.toList.unsafeRunSync()
    state should contain theSameElementsAs List(
      CounterState(Key(ItemScope(ItemId("p12")), FeatureName("c2")), 1),
      CounterState(Key(ItemScope(ItemId("p13")), FeatureName("c2")), 2)
    )
  }

}
