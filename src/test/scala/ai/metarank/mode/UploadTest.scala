package ai.metarank.mode

import ai.metarank.mode.upload.Upload
import ai.metarank.util.ListSource
import cats.effect.unsafe.implicits.global
import com.github.microwww.redis.RedisServer
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureValue, Key, SString, ScalarValue, Timestamp}
import io.findify.featury.values.StoreCodec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import redis.clients.jedis.Jedis

import scala.concurrent.duration._
import scala.util.Random

class UploadTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {
  var service: RedisServer = _

  val port = 1024 + Random.nextInt(60000)

  override def beforeAll() = {
    val service = new RedisServer()
    service.listener("localhost", port)
  }

  override def afterAll() = {
    service.close()
  }

  "upload job" should "write all events to redis" in {
    import TypeInfos._
    val source = ListSource[FeatureValue](
      (0 until 1000)
        .map(i =>
          ScalarValue(
            Key(Tag(Scope("user"), i.toString), FeatureName("f1"), Tenant("1")),
            Timestamp.now,
            SString(i.toString)
          )
        )
        .toList
    )
    val (_, close) = Upload.upload(source, "localhost", port, StoreCodec.JsonCodec, 1.second).allocated.unsafeRunSync()
    close.unsafeRunSync()

    val jedis = new Jedis("localhost", port)
    jedis.keys("*").size() shouldBe 1000
    jedis.close()
  }
}
