package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.FileClient
import ai.metarank.fstore.file.client.FileClient.{KeyVal, NumCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.State.CounterState
import ai.metarank.model.{FeatureValue, Key, State, Timestamp, Write}
import cats.effect.IO
import fs2.Stream

import scala.annotation.tailrec

case class FileCounterFeature(config: CounterConfig, db: FileClient, prefix: String, format: StoreFormat)
    extends CounterFeature {
  override def put(action: Write.Increment): IO[Unit] = for {
    kbytes <- IO(format.key.encode(prefix, action.key).getBytes)
    _      <- IO(db.inc(kbytes, action.inc))
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.CounterValue]] = for {
    valueOption <- IO(db.getInt(format.key.encode(prefix, key).getBytes))
  } yield {
    valueOption.map(value => CounterValue(key, ts, value))
  }
}

object FileCounterFeature {
  implicit val counterSource: StateSource[CounterState, FileCounterFeature] =
    new StateSource[CounterState, FileCounterFeature] {
      override def source(f: FileCounterFeature): Stream[IO, CounterState] =
        Stream
          .fromBlockingIterator[IO](f.db.firstN(s"${f.prefix}/${f.config.name.value}".getBytes(), Int.MaxValue), 128)
          .evalMap(kv =>
            IO.fromEither(f.format.key.decode(new String(kv.key))).map(k => CounterState(k, NumCodec.readInt(kv.value)))
          )
    }
}
