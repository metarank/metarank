package ai.metarank.fstore.redis

import ai.metarank.fstore.ScalarFeatureSuite
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Write.Put

class RedisScalarFeatureTest extends ScalarFeatureSuite with RedisFeatureTest[Put, ScalarFeature] {
  override def feature: ScalarFeature =
    RedisScalarFeature(config, client, "x")
}
