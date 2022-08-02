package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp.SET
import ai.metarank.fstore.redis.client.RedisReader
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.Put
import cats.effect.IO
import cats.effect.std.Queue
import io.circe.syntax._
import io.circe.parser._

case class RedisScalarFeature(
    config: ScalarConfig,
    queue: Queue[IO, RedisOp],
    client: RedisReader
) extends ScalarFeature {
  override def put(action: Put): IO[Unit] = {
    queue.offer(SET(action.key.asString, action.value.asJson.noSpaces))
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[ScalarValue]] = {
    client.get(key.asString).flatMap {
      case Some(value) => IO.fromEither(decode[Scalar](value)).map(s => Some(ScalarValue(key, ts, s)))
      case None        => IO.pure(None)
    }
  }
}
