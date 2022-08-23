package ai.metarank.fstore.memory

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Write.PeriodicIncrement
import com.github.blemale.scaffeine.Scaffeine

class MemPeriodicCounterTest extends PeriodicCounterSuite with MemTest[PeriodicIncrement, PeriodicCounterFeature] {
  override val feature: PeriodicCounterFeature = MemPeriodicCounter(config, Scaffeine().build())
}
