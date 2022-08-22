package ai.metarank.fstore.redis

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.model.Write.PutStatSample

class RedisStatsEstimatorFeatureTest
    extends StatsEstimatorSuite
    with RedisFeatureTest[PutStatSample, RedisStatsEstimatorFeature] {
  override def feature: RedisStatsEstimatorFeature = RedisStatsEstimatorFeature(config, client, "x")
}
