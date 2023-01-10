package ai.metarank.fstore.redis

import ai.metarank.fstore.CounterSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.State.CounterState
import ai.metarank.model.Write.Increment
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisCounterFeatureTest extends CounterSuite with RedisTest {
  override def feature(config: CounterConfig): RedisCounterFeature =
    RedisCounterFeature(config, client, "x", JsonStoreFormat)

  it should "accept state" in {
    val c = config.copy(name = FeatureName("rcs"))
    val f = feature(c)
    RedisCounterFeature.counterSink.sink(f, fs2.Stream(CounterState(TestKey(c, "a"), 7))).unsafeRunSync()
  }
}
