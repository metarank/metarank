package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Increment
import ai.metarank.util.Logging
import cats.effect.IO

import scala.util.Try

case class RedisCounterFeature(config: CounterConfig, client: RedisClient, prefix: String)
    extends CounterFeature
    with Logging
    with RedisFeature {
  override def put(action: Increment): IO[Unit] = {
    client.incrBy(str(action.key), action.inc).void
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[CounterValue]] = {
    client.get(str(key)).flatMap {
      case Some(str) =>
        debug(s"loading $key from redis") *> IO
          .fromTry(Try(str.toLong))
          .map(x => Some(CounterValue(key, ts, x)))
      case None => IO.pure(None)
    }
  }
}
