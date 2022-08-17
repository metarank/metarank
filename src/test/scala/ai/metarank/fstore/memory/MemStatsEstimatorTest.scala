package ai.metarank.fstore.memory

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.model.Feature.StatsEstimator
import ai.metarank.model.Write.PutStatSample
import com.github.blemale.scaffeine.Scaffeine

class MemStatsEstimatorTest extends StatsEstimatorSuite with MemTest[PutStatSample, StatsEstimator] {
  override val feature: StatsEstimator = MemStatsEstimator(config, Scaffeine().build())
}
