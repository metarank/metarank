package ai.metarank.fstore.memory

import ai.metarank.fstore.CounterSuite
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.Key
import ai.metarank.model.Write.Increment
import com.github.blemale.scaffeine.Scaffeine

class MemCounterTest extends CounterSuite {
  override def feature(config: CounterConfig) = MemCounter(config)
}
