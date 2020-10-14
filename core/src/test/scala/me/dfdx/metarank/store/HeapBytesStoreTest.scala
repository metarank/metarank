package me.dfdx.metarank.store

import me.dfdx.metarank.model.Featurespace

class HeapBytesStoreTest extends StoreTestSuite {
  override def makeStore(fs: Featurespace): Store = new HeapBytesStore(fs)
}
