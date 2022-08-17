package ai.metarank.fstore.memory

import ai.metarank.fstore.MapFeatureSuite
import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Write.PutTuple
import com.github.blemale.scaffeine.Scaffeine

class MemMapFeatureTest extends MapFeatureSuite with MemTest[PutTuple, MapFeature] {
  override val feature = MemMapFeature(config, Scaffeine().build())
}
