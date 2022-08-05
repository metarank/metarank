package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisClient}
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import org.scalatest.{BeforeAndAfterAll, Suite}

trait RedisTest extends BeforeAndAfterAll { this: Suite =>
  lazy val client                  = RedisClient.create("localhost", 6379, 0).allocated.unsafeRunSync()._1

  override def beforeAll() = {
    super.beforeAll()
    client.commands.flushall().get()
  }

}
