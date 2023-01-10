package ai.metarank.fstore.filec

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.fstore.file.{FilePeriodicCounterFeature, FileTest}
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.State.PeriodicCounterState
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class FilePeriodicCounterTest extends PeriodicCounterSuite with FileTest {
  override def feature(config: PeriodicCounterConfig): FilePeriodicCounterFeature =
    FilePeriodicCounterFeature(config, db, "x", BinaryStoreFormat)

  it should "pull state" in {
    val c = config.copy(name = FeatureName("pcs"))
    val f = feature(c)
    f.put(PeriodicIncrement(TestKey(c, "a"), now, 1)).unsafeRunSync()
    f.put(PeriodicIncrement(TestKey(c, "a"), now, 1)).unsafeRunSync()
    val state = FilePeriodicCounterFeature.pcState.source(f).compile.toList.unsafeRunSync()
    state shouldBe List(
      PeriodicCounterState(TestKey(c, "a"), Map(now.toStartOfPeriod(c.period) -> 2))
    )
  }
}
