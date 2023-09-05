package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.SortedDB
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.State.CounterState
import ai.metarank.model.{FeatureValue, Key, Timestamp, Write}
import cats.effect.IO
import fs2.Stream

case class FileCounterFeature(config: CounterConfig, db: SortedDB[Int], format: StoreFormat) extends CounterFeature {
  override def put(action: Write.Increment): IO[Unit] = for {
    key   <- IO(format.key.encodeNoPrefix(action.key))
    value <- IO(db.get(key))
    _     <- IO(db.put(key, value.getOrElse(0) + action.inc))
  } yield {}

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.CounterValue]] = for {
    valueOption <- IO(db.get(format.key.encodeNoPrefix(key)))
  } yield {
    valueOption.map(value => CounterValue(key, ts, value, config.ttl))
  }
}

object FileCounterFeature {
  implicit val counterSource: StateSource[CounterState, FileCounterFeature] =
    new StateSource[CounterState, FileCounterFeature] {
      override def source(f: FileCounterFeature): Stream[IO, CounterState] = {
        Stream
          .fromBlockingIterator[IO](f.db.all(), 128)
          .evalMap(kv => IO.fromEither(f.format.key.decodeNoPrefix(kv._1)).map(k => CounterState(k, kv._2)))
      }
    }
}
