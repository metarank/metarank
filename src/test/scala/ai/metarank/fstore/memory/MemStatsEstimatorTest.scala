package ai.metarank.fstore.memory

import ai.metarank.fstore.StatsEstimatorSuite
import ai.metarank.model.Feature.StatsEstimatorFeature
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.Write.PutStatSample
import com.github.blemale.scaffeine.Scaffeine

class MemStatsEstimatorTest extends StatsEstimatorSuite {
  override def feature(config: StatsEstimatorConfig): StatsEstimatorFeature =
    MemStatsEstimator(config, Scaffeine().build())
}
