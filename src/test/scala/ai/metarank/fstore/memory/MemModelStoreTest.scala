package ai.metarank.fstore.memory

import ai.metarank.fstore.{ModelStoreSuite, Persistence}

class MemModelStoreTest extends ModelStoreSuite {
  override val store: Persistence.ModelStore = MemModelStore()
}
