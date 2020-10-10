package me.dfdx.metarank.store

class HeapBytesStoreTest extends StoreTestSuite {
  override lazy val store = new HeapBytesStore()
}
