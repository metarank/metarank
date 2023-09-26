package ai.metarank.fstore.redis

import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.State.CounterState
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Increment
import ai.metarank.util.Logging
import cats.effect.IO

import scala.util.Try

case class RedisCounterFeature(config: CounterConfig, client: RedisClient, prefix: String, format: StoreFormat)
    extends CounterFeature
    with Logging {
  override def put(action: Increment): IO[Unit] = for {
    key <- IO.pure(format.key.encode(prefix, action.key))
    _   <- client.incrBy(key, action.inc).void
    _   <- client.expire(key, config.ttl)
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[CounterValue]] = {
    client.get(format.key.encode(prefix, key)).flatMap {
      case Some(str) =>
        IO.fromOption(new String(str).toLongOption)(new Exception("cannot parse long $str"))
          .map(x => Some(CounterValue(key, ts, x, config.ttl)))
      case None => IO.pure(None)
    }
  }
}

object RedisCounterFeature {
  implicit val counterSink: StateSink[CounterState, RedisCounterFeature] =
    new StateSink[CounterState, RedisCounterFeature] {
      override def sink(f: RedisCounterFeature, state: fs2.Stream[IO, CounterState]): IO[TransferResult] =
        state
          .evalMap(c =>
            for {
              key <- IO(f.format.key.encode(f.prefix, c.key))
              _   <- f.client.incrBy(key, c.value)
              _   <- f.client.expire(key, f.config.ttl)
            } yield { 1 }
          )
          .compile
          .fold(0)(_ + _)
          .map(TransferResult.apply)

    }
}
