package ai.metarank.fstore.memory

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.model.Feature.FreqEstimatorFeature
import ai.metarank.model.Write.PutFreqSample
import com.github.blemale.scaffeine.Scaffeine

class MemFreqEstimatorTest extends FreqEstimatorSuite with MemTest[PutFreqSample, FreqEstimatorFeature] {
  override val feature: FreqEstimatorFeature = MemFreqEstimator(config, Scaffeine().build())
}
