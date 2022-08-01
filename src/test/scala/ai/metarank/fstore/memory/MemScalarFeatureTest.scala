package ai.metarank.fstore.memory

import ai.metarank.fstore.ScalarFeatureSuite
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Write.Put
import com.github.blemale.scaffeine.Scaffeine

class MemScalarFeatureTest extends ScalarFeatureSuite with MemTest[Put, ScalarFeature] {
  override val feature: ScalarFeature = MemScalarFeature(config, Scaffeine().build())
}
