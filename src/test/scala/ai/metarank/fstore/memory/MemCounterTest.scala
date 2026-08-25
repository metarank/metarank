package ai.metarank.fstore.memory

import ai.metarank.fstore.CounterSuite
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig

class MemCounterTest extends CounterSuite {
  override def feature(config: CounterConfig) = MemCounter(config)
}
