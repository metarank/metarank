package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.codec.{KCodec, StoreFormat, VCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSink
import ai.metarank.fstore.transfer.StateSink.TransferResult
import ai.metarank.model.Feature.BoundedListFeature
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Scalar.{SDouble, SString}
import ai.metarank.model.State.BoundedListState
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
    format: StoreFormat
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
      values     <- client.lrange(format.key.encode(prefix, key), 0, config.count)
      timeValues <- values.map(bytes => IO.fromEither(format.timeValue.decode(bytes))).sequence
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

object RedisBoundedListFeature {
  implicit val listStateSink: StateSink[BoundedListState, RedisBoundedListFeature] =
    new StateSink[BoundedListState, RedisBoundedListFeature] {
      override def sink(f: RedisBoundedListFeature, state: fs2.Stream[IO, BoundedListState]): IO[TransferResult] =
        state
          .evalMap(s => {
            val key    = f.format.key.encode(f.prefix, s.key)
            val values = s.values.map(f.format.timeValue.encode)
            f.client.lpush(key, values).map(_ => 1)
          })
          .compile
          .fold(0)(_ + _)
          .map(TransferResult.apply)
    }
}
