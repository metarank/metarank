package ai.metarank.fstore.redis

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.State.StatsEstimatorState
import ai.metarank.model.Write.PutStatSample
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisStatsEstimatorFeatureTest extends StatsEstimatorSuite with RedisTest {
  override def feature(config: StatsEstimatorConfig): RedisStatsEstimatorFeature =
    RedisStatsEstimatorFeature(config, client, "x", JsonStoreFormat)

  it should "accept state" in {
    val f = feature(config)
    RedisStatsEstimatorFeature.statsSink
      .sink(f, fs2.Stream(StatsEstimatorState(TestKey(config, "a"), Array(1.0))))
      .unsafeRunSync()
  }
}
