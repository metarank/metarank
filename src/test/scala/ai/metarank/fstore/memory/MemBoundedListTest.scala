package ai.metarank.fstore.memory

import ai.metarank.fstore.BoundedListSuite
import ai.metarank.model.Feature.BoundedList
import ai.metarank.model.Write.Append
import com.github.blemale.scaffeine.Scaffeine

class MemBoundedListTest extends BoundedListSuite with MemTest[Append, BoundedList] {
  override val feature: BoundedList = MemBoundedList(config, Scaffeine().build())
}
