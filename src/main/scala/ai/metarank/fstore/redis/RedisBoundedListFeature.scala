package ai.metarank.fstore.redis

import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.BoundedListFeature
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Scalar.{SDouble, SString}
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.Append
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.syntax._
import io.circe.parser._
import cats.implicits._

case class RedisBoundedListFeature(
    config: BoundedListConfig,
    client: RedisClient,
    prefix: String
) extends BoundedListFeature
    with Logging
    with RedisFeature {
  override def put(action: Append): IO[Unit] = {
    val records = action.value match {
      case Scalar.SStringList(value) => value.map(v => TimeValue(action.ts, SString(v)))
      case Scalar.SDoubleList(value) => value.map(v => TimeValue(action.ts, SDouble(v)))
      case other                     => List(TimeValue(action.ts, other))
    }
    if (records.nonEmpty) {
      val key = str(action.key)
      client
        .lpush(key, records.map(_.asJson.noSpaces))
        .flatMap(_ => client.ltrim(key, 0, config.count).void)
    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[BoundedListValue]] = {
    for {
      values     <- client.lrange(str(key), 0, config.count)
      timeValues <- values.map(json => IO.fromEither(decode[TimeValue](json))).sequence
    } yield {
      timeValues.headOption match {
        case None => None
        case Some(head) =>
          val threshold = head.ts.minus(config.duration)
          Some(BoundedListValue(key, ts, timeValues.filter(_.ts.isAfterOrEquals(threshold))))
      }
    }
  }

}
