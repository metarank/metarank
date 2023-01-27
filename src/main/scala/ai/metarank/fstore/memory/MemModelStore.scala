package ai.metarank.fstore.memory

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.Persistence.{KVStore, ModelName, ModelStore}
import ai.metarank.ml.{Context, Model, Predictor}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemModelStore(cache: Cache[ModelName, Model[_]] = Scaffeine().build()) extends ModelStore {
  override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
      key: ModelName,
      pred: Predictor[C, T, M]
  ): IO[Option[M]] = IO(cache.getIfPresent(key).map(_.asInstanceOf[M]))

  override def put(value: Model[_]): IO[Unit] = IO {
    cache.put(ModelName(value.name), value)
  }

}
