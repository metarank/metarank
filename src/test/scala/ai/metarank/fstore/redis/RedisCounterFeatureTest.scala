package ai.metarank.fstore.redis

import ai.metarank.fstore.CounterSuite
import ai.metarank.model.Write.Increment

class RedisCounterFeatureTest extends CounterSuite with RedisFeatureTest[Increment, RedisCounterFeature] {
  override def feature: RedisCounterFeature = RedisCounterFeature(config, client)
}
