package me.dfdx.metarank.store

import me.dfdx.metarank.model.Featurespace

class HeapStoreTest extends StoreTestSuite {
  override def makeStore(fs: Featurespace) = new HeapStore(fs)
}
