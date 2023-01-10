package ai.metarank.fstore.file

import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.file.client.FileClient
import ai.metarank.fstore.transfer.StateSource
import ai.metarank.model.Feature.ScalarFeature
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.State.ScalarState
import ai.metarank.model.{FeatureValue, Key, State, Timestamp, Write}
import cats.effect.IO

case class FileScalarFeature(config: ScalarConfig, db: FileClient, prefix: String, format: StoreFormat)
    extends ScalarFeature {
  override def put(action: Write.Put): IO[Unit] = IO {
    db.put(format.key.encode(prefix, action.key).getBytes(), format.scalar.encode(action.value))
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[FeatureValue.ScalarValue]] = {
    for {
      bytes <- IO(db.get(format.key.encode(prefix, key).getBytes()))
      parsed <- bytes match {
        case Some(value) => IO.fromEither(format.scalar.decode(value)).map(s => Some(ScalarValue(key, ts, s)))
        case None        => IO.pure(None)
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
          .fromIterator[IO](f.db.firstN(s"${f.prefix}/${f.config.name.value}".getBytes(), Int.MaxValue), 128)
          .evalMap(kv =>
            for {
              key   <- IO.fromEither(f.format.key.decode(new String(kv.key)))
              value <- IO.fromEither(f.format.scalar.decode(kv.value))
            } yield {
              ScalarState(key, value)
            }
          )

    }
}
