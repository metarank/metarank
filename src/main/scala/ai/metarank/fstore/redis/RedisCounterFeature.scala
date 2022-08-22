package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.Counter
import ai.metarank.model.Feature.Counter.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Increment
import cats.effect.IO
import scala.util.Try

case class RedisCounterFeature(config: CounterConfig, client: RedisClient) extends Counter {
  override def put(action: Increment): IO[Unit] = {
    client.incrBy(action.key.asString, action.inc).void
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[CounterValue]] = {
    client.get(key.asString).flatMap {
      case Some(str) => IO.fromTry(Try(str.toLong)).map(x => Some(CounterValue(key, ts, x)))
      case None      => IO.pure(None)
    }
  }
}
