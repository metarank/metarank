package ai.metarank.fstore.memory

import ai.metarank.fstore.BoundedListSuite
import ai.metarank.model.Feature.BoundedListFeature
import ai.metarank.model.Write.Append
import com.github.blemale.scaffeine.Scaffeine

class MemBoundedListTest extends BoundedListSuite with MemTest[Append, BoundedListFeature] {
  override val feature: BoundedListFeature = MemBoundedList(config, Scaffeine().build())
}
