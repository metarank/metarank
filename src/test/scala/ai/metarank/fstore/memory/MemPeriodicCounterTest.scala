package ai.metarank.fstore.memory

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.Write.PeriodicIncrement
import com.github.blemale.scaffeine.Scaffeine

class MemPeriodicCounterTest extends PeriodicCounterSuite {
  override def feature(config: PeriodicCounterConfig): PeriodicCounterFeature =
    MemPeriodicCounter(config, Scaffeine().build())
}
