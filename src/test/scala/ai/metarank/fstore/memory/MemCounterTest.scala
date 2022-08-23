package ai.metarank.fstore.memory

import ai.metarank.fstore.CounterSuite
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Key
import ai.metarank.model.Write.Increment
import com.github.blemale.scaffeine.Scaffeine

class MemCounterTest extends CounterSuite with MemTest[Increment, CounterFeature] {
  override val feature = MemCounter(config, Scaffeine().build[Key, Long]())
}
