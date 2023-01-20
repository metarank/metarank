package ai.metarank.fstore.redis

import ai.metarank.fstore.MapFeatureSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.Scalar.SString
import ai.metarank.model.State.MapState
import ai.metarank.model.Write.PutTuple
import ai.metarank.util.TestKey
import cats.effect.unsafe.implicits.global

class RedisMapFeatureTest extends MapFeatureSuite with RedisTest {
  override def feature(config: MapConfig): RedisMapFeature =
    RedisMapFeature(config, client, "x", JsonStoreFormat)

  it should "accept state" in {
    val f = feature()
    RedisMapFeature.mapSink
      .sink(feature(config), fs2.Stream(MapState(TestKey(config, "a"), Map("foo" -> SString("bar")))))
      .unsafeRunSync()
  }
}
