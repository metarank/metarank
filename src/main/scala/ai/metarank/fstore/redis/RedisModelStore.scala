package ai.metarank.fstore.redis

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.Persistence.{ModelName, ModelStore}
import ai.metarank.fstore.codec.{KCodec, VCodec}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.util.Logging
import cats.effect.IO

case class RedisModelStore(client: RedisClient, prefix: String)(implicit kc: KCodec[ModelName], vc: VCodec[Array[Byte]])
    extends ModelStore
    with Logging {
  override def put(value: Model[_]): IO[Unit] = for {
    bytesOption <- IO(value.save())
    _           <- info(s"serialized model ${value.name}, size=${bytesOption.map(_.length)}")
    _ <- bytesOption match {
      case None        => IO.unit
      case Some(bytes) => client.set(kc.encode(prefix, ModelName(value.name)), vc.encode(bytes))
    }
  } yield {}

  override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
      key: ModelName,
      pred: Predictor[C, T, M]
  ): IO[Option[M]] = for {
    bytesOption <- client.get(kc.encode(prefix, key))
    model <- bytesOption match {
      case None =>
        pred.load(None) match {
          case Left(error)  => IO.raiseError(error)
          case Right(value) => IO(Some(value))
        }
      case Some(bytes) =>
        pred.load(Some(bytes)) match {
          case Left(error)  => IO.raiseError(error)
          case Right(value) => IO(Some(value))
        }
    }
  } yield {
    model
  }
}
