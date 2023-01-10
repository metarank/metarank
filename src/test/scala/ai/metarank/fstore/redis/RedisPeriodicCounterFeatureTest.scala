package ai.metarank.fstore.redis

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.State.PeriodicCounterState
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisPeriodicCounterFeatureTest extends PeriodicCounterSuite with RedisTest {
  override def feature(config: PeriodicCounterConfig): RedisPeriodicCounterFeature =
    RedisPeriodicCounterFeature(config, client, "x", JsonStoreFormat)

  it should "accept state" in {
    val f = feature(config)
    RedisPeriodicCounterFeature.periodicSink
      .sink(f, fs2.Stream(PeriodicCounterState(TestKey(config, "a"), Map(now -> 1))))
      .unsafeRunSync()
  }
}
