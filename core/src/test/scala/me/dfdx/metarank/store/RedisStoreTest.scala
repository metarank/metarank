package me.dfdx.metarank.store

import com.github.fppt.jedismock.RedisServer
import me.dfdx.metarank.model.Featurespace
import org.scalatest.BeforeAndAfterAll

class RedisStoreTest extends StoreTestSuite with BeforeAndAfterAll {
  var redisServer: RedisServer = _
  import scala.concurrent.ExecutionContext.Implicits.global

  override def makeStore(fs: Featurespace) = new RedisStore(fs, "localhost", 11111)

  override def beforeAll() = {
    redisServer = RedisServer.newRedisServer(11111)
    redisServer.start()
  }

  override def afterAll() = {
    redisServer.stop()
  }
}
