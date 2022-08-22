package ai.metarank.fstore.memory

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.model.Feature.StatsEstimatorFeature
import ai.metarank.model.Write.PutStatSample
import com.github.blemale.scaffeine.Scaffeine

class MemStatsEstimatorTest extends StatsEstimatorSuite with MemTest[PutStatSample, StatsEstimatorFeature] {
  override val feature: StatsEstimatorFeature = MemStatsEstimator(config, Scaffeine().build())
}
