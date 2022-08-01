package ai.metarank.fstore.memory

import ai.metarank.fstore.FreqEstimatorSuite
import ai.metarank.model.Feature.FreqEstimator
import ai.metarank.model.Write.PutFreqSample
import com.github.blemale.scaffeine.Scaffeine

class MemFreqEstimatorTest extends FreqEstimatorSuite with MemTest[PutFreqSample, FreqEstimator] {
  override val feature: FreqEstimator = MemFreqEstimator(config, Scaffeine().build())
}
