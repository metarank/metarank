package ai.metarank.fstore.redis

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.model.Write.PutFreqSample


class RedisFreqEstimatorFeatureTest
    extends FreqEstimatorSuite
    with RedisTest[PutFreqSample, RedisFreqEstimatorFeature] {
  override def feature: RedisFreqEstimatorFeature = RedisFreqEstimatorFeature(config, pipeline, client)
}
