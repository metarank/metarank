package ai.metarank.fstore.file

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.State.StatsEstimatorState
import ai.metarank.model.Write.PutStatSample
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class FileStatsEstimatorTest extends StatsEstimatorSuite with FileTest {
  override def feature(config: StatsEstimatorConfig): FileStatsEstimatorFeature =
    FileStatsEstimatorFeature(config, db, "x", BinaryStoreFormat)

  it should "pull state" in {
    val c = config.copy(name = FeatureName("sss"))
    val f = feature(c)
    f.put(PutStatSample(TestKey(c, "a"), now, 1.0)).unsafeRunSync()
    f.put(PutStatSample(TestKey(c, "a"), now, 1.0)).unsafeRunSync()
    f.put(PutStatSample(TestKey(c, "a"), now, 1.0)).unsafeRunSync()
    val state = FileStatsEstimatorFeature.statsSource.source(f).compile.toList.unsafeRunSync()
    state shouldBe List(StatsEstimatorState(TestKey(c, "a"), Array(1.0, 1.0, 1.0)))
  }
}
