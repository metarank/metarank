package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.State.ScalarState
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO

case class RedisScalarFeature(
    config: ScalarConfig,
    client: RedisClient,
    prefix: String,
    format: StoreFormat
) extends ScalarFeature
    with Logging {
  override def put(action: Put): IO[Unit] = for {
    key <- IO(format.key.encode(prefix, action.key))
    _   <- client.set(key, format.scalar.encode(action.value), config.ttl)
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[ScalarValue]] = {
    client.get(format.key.encode(prefix, key)).flatMap {
      case Some(value) =>
        IO
          .fromEither(format.scalar.decode(value))
          .map(s => Some(ScalarValue(key, ts, s, config.ttl)))
      case None => IO.pure(None)
    }
  }
}

object RedisScalarFeature {
  implicit val scalarSink: StateSink[ScalarState, RedisScalarFeature] = new StateSink[ScalarState, RedisScalarFeature] {
    override def sink(f: RedisScalarFeature, state: fs2.Stream[IO, ScalarState]): IO[TransferResult] =
      state
        .evalMap(s =>
          f.client.set(f.format.key.encode(f.prefix, s.key), f.format.scalar.encode(s.value), f.config.ttl).map(_ => 1)
        )
        .compile
        .fold(0)(_ + _)
        .map(TransferResult.apply)

  }
}
