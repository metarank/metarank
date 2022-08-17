package ai.metarank.fstore.redis

import ai.metarank.fstore.FeatureSuite
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisClient}
import ai.metarank.model.{Feature, FeatureValue, Write}
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import com.github.microwww.redis.RedisServer
import org.scalatest.BeforeAndAfterAll
import redis.clients.jedis.Jedis

import scala.util.Random

trait RedisFeatureTest[W <: Write, F <: Feature[W, _ <: FeatureValue]] extends BeforeAndAfterAll with RedisTest {
  this: FeatureSuite[W] =>

  def feature: F
  def write(values: List[W]): Option[FeatureValue] = {
    values.foldLeft(Option.empty[FeatureValue])((_, inc) => {
      feature.put(inc).unsafeRunSync()
      feature.computeValue(inc.key, inc.ts).unsafeRunSync()
    })
  }
}

object RedisFeatureTest {}
