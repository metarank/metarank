package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.blemale.scaffeine.Cache
import io.circe.syntax._
import io.circe.parser._

case class RedisScalarFeature(
    config: ScalarConfig,
    client: RedisClient,
    prefix: String
) extends ScalarFeature
    with RedisFeature
    with Logging {
  override def put(action: Put): IO[Unit] = {
    debug(s"writing scalar key=${action.key}")
    client.set(str(action.key), action.value.asJson.noSpaces).void
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[ScalarValue]] = {
    client.get(str(key)).flatMap {
      case Some(value) =>
        debug(s"loading scalar $key") *> IO
          .fromEither(decode[Scalar](value))
          .map(s => Some(ScalarValue(key, ts, s)))
      case None => IO.pure(None)
    }
  }
}
