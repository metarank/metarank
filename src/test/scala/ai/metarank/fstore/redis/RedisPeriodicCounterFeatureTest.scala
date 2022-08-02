package ai.metarank.fstore.redis

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.model.Write.PeriodicIncrement

class RedisPeriodicCounterFeatureTest
    extends PeriodicCounterSuite
    with RedisTest[PeriodicIncrement, RedisPeriodicCounterFeature] {
  override def feature: RedisPeriodicCounterFeature =
    RedisPeriodicCounterFeature(config, pipeline, client)
}
