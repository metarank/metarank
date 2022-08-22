package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, PipelineConfig}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import cats.implicits._

class RedisKVStorePipelineTest extends AnyFlatSpec with Matchers with RedisTest {
  override def cache    = CacheConfig(ttl = 0.seconds, maxSize = 0)
  override def pipeline = PipelineConfig(10, 500.millis)

  it should "do size-triggered flush" in {
    val keys   = (0 until 100).map(i => s"key$i").toList
    val ok     = keys.map(i => client.set(i, "foo")).sequence.unsafeRunSync()
    val result = client.mget(keys).unsafeRunSync()
    result shouldBe keys.map(k => k -> "foo").toMap
  }

  it should "do time-triggered flush" in {
    val keys   = (0 until 5).map(i => s"key$i").toList
    val ok     = keys.map(i => client.set(i, "foo")).sequence.flatMap(_ => IO.sleep(600.millis)).unsafeRunSync()
    val result = client.mget(keys).unsafeRunSync()
    result shouldBe keys.map(k => k -> "foo").toMap
  }

}
