package ai.metarank.fstore.memory

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.model.Feature.FreqEstimatorFeature
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import com.github.blemale.scaffeine.Scaffeine

class MemFreqEstimatorTest extends FreqEstimatorSuite {
  override def feature(config: FreqEstimatorConfig): FreqEstimatorFeature =
    MemFreqEstimator(config, Scaffeine().build())
}
