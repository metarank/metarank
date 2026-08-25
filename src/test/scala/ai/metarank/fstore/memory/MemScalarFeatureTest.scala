package ai.metarank.fstore.memory

import ai.metarank.fstore.ScalarFeatureSuite
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import com.github.blemale.scaffeine.Scaffeine

class MemScalarFeatureTest extends ScalarFeatureSuite {
  override def feature(config: ScalarConfig): ScalarFeature = MemScalarFeature(config, Scaffeine().build())
}
