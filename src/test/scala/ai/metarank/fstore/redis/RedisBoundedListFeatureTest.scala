package ai.metarank.fstore.redis

import ai.metarank.fstore.BoundedListSuite
import ai.metarank.fstore.redis.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Write.Append

class RedisBoundedListFeatureTest extends BoundedListSuite with RedisFeatureTest[Append, RedisBoundedListFeature] {
  override def feature: RedisBoundedListFeature = RedisBoundedListFeature(config, client, "x", JsonStoreFormat)
}
