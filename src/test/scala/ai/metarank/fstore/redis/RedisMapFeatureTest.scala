package ai.metarank.fstore.redis

import ai.metarank.fstore.MapFeatureSuite
import ai.metarank.fstore.redis.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Write.PutTuple

class RedisMapFeatureTest extends MapFeatureSuite with RedisFeatureTest[PutTuple, RedisMapFeature] {
  override def feature: RedisMapFeature =
    RedisMapFeature(config, client, "x", JsonStoreFormat)
}
