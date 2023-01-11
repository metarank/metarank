package ai.metarank.fstore.redis

import ai.metarank.fstore.ScalarFeatureSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.Scalar.SString
import ai.metarank.model.State.ScalarState
import ai.metarank.model.Write.Put
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisScalarFeatureTest extends ScalarFeatureSuite with RedisTest {
  override def feature(config: ScalarConfig): RedisScalarFeature =
    RedisScalarFeature(config, client, "x", JsonStoreFormat)

  it should "Accept state" in {
    val f = feature(config)
    RedisScalarFeature.scalarSink.sink(f, fs2.Stream(ScalarState(TestKey(config, "a"), SString("foo")))).unsafeRunSync()
  }
}
