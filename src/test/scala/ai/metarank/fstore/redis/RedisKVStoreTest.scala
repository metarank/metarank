package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisReader}
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import com.github.microwww.redis.RedisServer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RedisKVStoreTest extends AnyFlatSpec with Matchers {
  lazy val client = RedisReader.create("localhost", 6379, 0).allocated.unsafeRunSync()._1
  lazy val writer = RedisPipeline.create("localhost", 6379, 0).allocated.unsafeRunSync()._1
  lazy val queue  = Queue.unbounded[IO, RedisOp].unsafeRunSync()
  lazy val kv     = RedisKVStore[String]("foo", queue, RedisFeatureTest.client)

  it should "get empty" in {
    kv.get(List("a", "b")).unsafeRunSync() shouldBe Map.empty
  }
}
