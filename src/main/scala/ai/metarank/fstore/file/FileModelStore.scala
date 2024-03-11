package ai.metarank.fstore.file

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelStore
import ai.metarank.fstore.file.client.{HashDB, SortedDB}
import ai.metarank.ml.{Context, Model, Predictor}
import cats.effect.IO

case class FileModelStore(db: HashDB[Array[Byte]]) extends ModelStore {
  override def put(value: Model[_]): IO[Unit] = IO {
    value.save() match {
      case None        => IO.unit
      case Some(bytes) => IO(db.put(value.name, bytes))
    }
  }

  override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
      key: Persistence.ModelName,
      pred: Predictor[C, T, M]
  ): IO[Option[M]] = {
    pred.load(db.get(key.name)).map(Option.apply)
  }
}
