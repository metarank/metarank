package ai.metarank.fstore.memory

import ai.metarank.fstore.PeriodicCounterSuite
import ai.metarank.model.Feature.PeriodicCounter
import ai.metarank.model.Write.PeriodicIncrement
import com.github.blemale.scaffeine.Scaffeine

class MemPeriodicCounterTest extends PeriodicCounterSuite with MemTest[PeriodicIncrement, PeriodicCounter] {
  override val feature: PeriodicCounter = MemPeriodicCounter(config, Scaffeine().build())
}
