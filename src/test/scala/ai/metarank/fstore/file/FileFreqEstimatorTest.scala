package ai.metarank.fstore.file

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.State.FreqEstimatorState
import ai.metarank.model.Write.PutFreqSample
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class FileFreqEstimatorTest extends FreqEstimatorSuite with FileTest {
  override def feature(config: FreqEstimatorConfig): FileFreqEstimatorFeature =
    FileFreqEstimatorFeature(config, db, "x", BinaryStoreFormat)

  it should "pull state" in {
    val c = config.copy(name = FeatureName("ffe"))
    val f = feature(c)
    f.put(PutFreqSample(TestKey(c, "a"), now, "a")).unsafeRunSync()
    f.put(PutFreqSample(TestKey(c, "a"), now, "b")).unsafeRunSync()
    db.sync()
    val state = FileFreqEstimatorFeature.fileFreqSource.source(f).compile.toList.unsafeRunSync()
    state.flatMap(_.values) should contain theSameElementsAs List("a", "b")
  }
}
