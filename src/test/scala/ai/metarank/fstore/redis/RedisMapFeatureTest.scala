package ai.metarank.fstore.redis

import ai.metarank.fstore.MapFeatureSuite
import ai.metarank.model.Write.PutTuple

class RedisMapFeatureTest extends MapFeatureSuite with RedisTest[PutTuple, RedisMapFeature] {
  override def feature: RedisMapFeature =
    RedisMapFeature(config, pipeline, client)
}
