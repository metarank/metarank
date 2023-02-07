package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.fstore.redis.RedisPersistence.Prefix
import ai.metarank.fstore.{ModelStoreSuite, Persistence}

class RedisModelStoreTest extends ModelStoreSuite with RedisTest {
  lazy val fmt                               = BinaryStoreFormat
  override val store: Persistence.ModelStore = RedisModelStore(client, Prefix.MODELS)(fmt.modelName, fmt.model)
}
