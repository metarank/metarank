package ai.metarank.fstore.redis

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.fstore.redis.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Write.PeriodicIncrement

class RedisPeriodicCounterFeatureTest
    extends PeriodicCounterSuite
    with RedisFeatureTest[PeriodicIncrement, RedisPeriodicCounterFeature] {
  override def feature: RedisPeriodicCounterFeature =
    RedisPeriodicCounterFeature(config, client, "x", JsonStoreFormat)
}
