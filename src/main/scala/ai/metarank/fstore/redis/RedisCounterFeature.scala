package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.INCRBY
import ai.metarank.fstore.redis.client.RedisReader
import ai.metarank.model.Feature.Counter
import ai.metarank.model.Feature.Counter.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Increment
import cats.effect.IO
import cats.effect.std.Queue
import scala.util.Try

case class RedisCounterFeature(config: CounterConfig, queue: Queue[IO, RedisOp], client: RedisReader) extends Counter {
  override def put(action: Increment): IO[Unit] = {
    queue.offer(INCRBY(action.key.asString, action.inc))
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[CounterValue]] = {
    client.get(key.asString).flatMap {
      case Some(str) => IO.fromTry(Try(str.toLong)).map(x => Some(CounterValue(key, ts, x)))
      case None      => IO.pure(None)
    }
  }
}
