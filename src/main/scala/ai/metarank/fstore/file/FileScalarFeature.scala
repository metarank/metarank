package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.{FileClient, HashDB}
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.State.ScalarState
import ai.metarank.model.{FeatureValue, Key, State, Timestamp, Write}
import cats.effect.IO

case class FileScalarFeature(config: ScalarConfig, db: HashDB[Array[Byte]], format: StoreFormat) extends ScalarFeature {
  override def put(action: Write.Put): IO[Unit] = IO {
    db.put(format.key.encodeNoPrefix(action.key), format.scalar.encode(action.value))
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.ScalarValue]] = {
    for {
      bytes <- IO(db.get(format.key.encodeNoPrefix(key)))
      parsed <- bytes match {
        case Some(value) =>
          IO.fromEither(format.scalar.decode(value)).map(s => Some(ScalarValue(key, ts, s, config.ttl)))
        case None => IO.pure(None)
      }
    } yield {
      parsed
    }
  }

}

object FileScalarFeature {
  implicit val fileScalarSource: StateSource[ScalarState, FileScalarFeature] =
    new StateSource[ScalarState, FileScalarFeature] {
      override def source(f: FileScalarFeature): fs2.Stream[IO, ScalarState] =
        fs2.Stream
          .fromIterator[IO](f.db.all(), 128)
          .evalMap(kv =>
            for {
              key   <- IO.fromEither(f.format.key.decodeNoPrefix(kv._1))
              value <- IO.fromEither(f.format.scalar.decode(kv._2))
            } yield {
              ScalarState(key, value)
            }
          )

    }
}
