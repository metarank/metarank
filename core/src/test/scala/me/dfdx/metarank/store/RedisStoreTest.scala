package me.dfdx.metarank.store

import com.github.fppt.jedismock.RedisServer
import me.dfdx.metarank.aggregation.Aggregation.EventTypeScope
import me.dfdx.metarank.aggregation.CountAggregation
import me.dfdx.metarank.aggregation.state.CircularReservoir
import me.dfdx.metarank.config.Config.EventType
import me.dfdx.metarank.model.Timestamp
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisStoreTest extends StoreTestSuite with BeforeAndAfterAll {
  var redisServer: RedisServer = _
  import scala.concurrent.ExecutionContext.Implicits.global
  override lazy val store = new RedisStore("localhost", 11111)

  override def beforeAll() = {
    redisServer = RedisServer.newRedisServer(11111)
    redisServer.start()
  }

  override def afterAll() = {
    redisServer.stop()
  }
}
