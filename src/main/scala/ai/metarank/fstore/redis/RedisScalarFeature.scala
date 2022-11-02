package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.codec.{KCodec, StoreFormat}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
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
  override def put(action: Put): IO[Unit] = {
    debug(s"writing scalar key=${action.key}")
    client.set(format.key.encode(prefix, action.key), format.scalar.encode(action.value)).void
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[ScalarValue]] = {
    client.get(format.key.encode(prefix, key)).flatMap {
      case Some(value) =>
        debug(s"loading scalar $key") *> IO
          .fromEither(format.scalar.decode(value))
          .map(s => Some(ScalarValue(key, ts, s)))
      case None => IO.pure(None)
    }
  }
}
