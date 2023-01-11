package ai.metarank.fstore.memory

import ai.metarank.fstore.MapFeatureSuite
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.Write.PutTuple
import com.github.blemale.scaffeine.Scaffeine

class MemMapFeatureTest extends MapFeatureSuite {
  override def feature(config: MapConfig) = MemMapFeature(config, Scaffeine().build())
}
