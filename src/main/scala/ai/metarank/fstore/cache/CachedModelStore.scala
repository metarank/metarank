package ai.metarank.fstore.cache

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelStore
import ai.metarank.ml.{Context, Model, Predictor}
import cats.effect.IO

case class CachedModelStore(fast: ModelStore, slow: ModelStore) extends ModelStore {
  override def put(value: Model[_]): IO[Unit] = fast.put(value) *> slow.put(value)

  override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
      key: Persistence.ModelName,
      pred: Predictor[C, T, M]
  ): IO[Option[M]] =
    fast.get(key, pred).flatMap {
      case Some(c) => IO.pure(Some(c))
      case None    => slow.get(key, pred)
    }
}
