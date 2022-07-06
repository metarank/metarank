package ai.metarank.mode

import com.github.microwww.redis.RedisServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UploadTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  var service: RedisServer = _

  override def beforeAll() = {
    val service = new RedisServer()
    service.listener("localhost", 6379)
  }

  override def afterAll() = {
    service.close()
  }

  it should "write all"
}
