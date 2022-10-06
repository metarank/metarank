package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.redis.encode.{EncodeFormat, KCodec, VCodec}
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
    prefix: String,
    format: EncodeFormat
) extends BoundedListFeature
    with Logging {
  override def put(action: Append): IO[Unit] = {
    val records = action.value match {
      case Scalar.SStringList(value) => value.map(v => TimeValue(action.ts, SString(v)))
      case Scalar.SDoubleList(value) => value.map(v => TimeValue(action.ts, SDouble(v)))
      case other                     => List(TimeValue(action.ts, other))
    }
    if (records.nonEmpty) {
      val key = format.key.encode(prefix, action.key)
      client
        .lpush(key, records.map(format.timeValue.encode))
        .flatMap(_ => client.ltrim(key, 0, config.count).void)
    } else {
      IO.unit
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[BoundedListValue]] = {
    for {
      values     <- client.lrange(keyEnc.encode(prefix, key), 0, config.count)
      timeValues <- values.map(bytes => IO.fromEither(timeValueCodec.decode(bytes))).sequence
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
