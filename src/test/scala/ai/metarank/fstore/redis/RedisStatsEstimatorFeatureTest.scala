package ai.metarank.fstore.redis

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.fstore.codec.StoreFormat.JsonStoreFormat
import ai.metarank.model.Write.PutStatSample

class RedisStatsEstimatorFeatureTest
    extends StatsEstimatorSuite
    with RedisFeatureTest[PutStatSample, RedisStatsEstimatorFeature] {
  override def feature: RedisStatsEstimatorFeature = RedisStatsEstimatorFeature(config, client, "x", JsonStoreFormat)
}
