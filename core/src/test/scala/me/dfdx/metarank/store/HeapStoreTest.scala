package me.dfdx.metarank.store

class HeapStoreTest extends StoreTestSuite {
  override lazy val store = new HeapStore()
}
