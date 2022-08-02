package ai.metarank.fstore.redis

import ai.metarank.fstore.FeatureSuite
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisReader}
import ai.metarank.model.{Feature, FeatureValue, Write}
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import com.github.microwww.redis.RedisServer
import org.scalatest.BeforeAndAfterAll
import redis.clients.jedis.Jedis

import scala.util.Random

trait RedisTest[W <: Write, F <: Feature[W, _ <: FeatureValue]] extends BeforeAndAfterAll {
  this: FeatureSuite[W] =>

  lazy val port                    = RedisTest.port
  var service: RedisServer         = RedisTest.service
  var client: RedisReader          = RedisTest.client
  val writer: RedisPipeline        = RedisTest.writer
  val pipeline: Queue[IO, RedisOp] = Queue.unbounded[IO, RedisOp].unsafeRunSync()

  def feature: F
  def write(values: List[W]): Option[FeatureValue] = {
    values.foldLeft(Option.empty[FeatureValue])((_, inc) => {
      feature.put(inc).unsafeRunSync()
      writer
        .batch(Iterator.continually(pipeline.tryTake.unsafeRunSync()).takeWhile(_.nonEmpty).flatten.toList)
        .unsafeRunSync()
      feature.computeValue(inc.key, inc.ts).unsafeRunSync()
    })
  }
}

object RedisTest {
  lazy val port = 1024 + Random.nextInt(60000)
  lazy val service = {
    val s = new RedisServer()
    s.listener("localhost", port)
    s
  }
  lazy val client = RedisReader.create("localhost", port, 0).allocated.unsafeRunSync()._1
  lazy val writer = RedisPipeline.create("localhost", port, 0).allocated.unsafeRunSync()._1
}
