package ai.metarank.fstore.redis

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.State.FreqEstimatorState
import ai.metarank.model.Write.PutFreqSample
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisFreqEstimatorFeatureTest extends FreqEstimatorSuite with RedisTest {
  override def feature(config: FreqEstimatorConfig): RedisFreqEstimatorFeature =
    RedisFreqEstimatorFeature(config, client, "x", JsonStoreFormat)

  it should "accept state" in {
    val c = config.copy(name = FeatureName("fs"))
    val f = feature(c)
    RedisFreqEstimatorFeature.freqSink
      .sink(f, fs2.Stream(FreqEstimatorState(TestKey(c, "a"), List("a", "b"))))
      .unsafeRunSync()
  }
}
