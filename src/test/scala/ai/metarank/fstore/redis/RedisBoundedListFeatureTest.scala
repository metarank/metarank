package ai.metarank.fstore.redis

import ai.metarank.fstore.BoundedListSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.SString
import ai.metarank.model.State.BoundedListState
import ai.metarank.model.Write.Append
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisBoundedListFeatureTest extends BoundedListSuite with RedisTest {
  override def feature(config: BoundedListConfig): RedisBoundedListFeature =
    RedisBoundedListFeature(config, client, "x", JsonStoreFormat)

  it should "accept state" in {
    val c = config.copy(name = FeatureName("bls"))
    val f = feature(c)
    RedisBoundedListFeature.listStateSink
      .sink(
        f,
        fs2.Stream(BoundedListState(TestKey(c, "a"), List(TimeValue(now, SString("foo")))))
      )
      .unsafeRunSync()
  }
}
