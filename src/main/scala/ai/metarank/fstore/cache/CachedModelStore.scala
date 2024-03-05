package ai.metarank.fstore.cache

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.{EventTicker, Persistence}
import ai.metarank.fstore.Persistence.{ModelName, ModelStore}
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.blemale.scaffeine.Scaffeine

import scala.concurrent.duration._

case class CachedModelStore(fast: ModelStore, slow: ModelStore) extends ModelStore {
  override def put(value: Model[_]): IO[Unit] = fast.put(value) *> slow.put(value)

  override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
      key: Persistence.ModelName,
      pred: Predictor[C, T, M]
  ): IO[Option[M]] =
    fast.get(key, pred).flatMap {
      case Some(c) => IO.pure(Some(c))
      case None =>
        slow.get(key, pred).flatMap {
          case Some(model) => fast.put(model) *> IO.pure(Some(model))
          case None        => IO.pure(None)
        }
    }
}

object CachedModelStore extends Logging {
  def createCache(ticker: EventTicker, size: Int = 32, expire: FiniteDuration = 1.hour) = Scaffeine()
    .ticker(ticker)
    .maximumSize(size)
    .expireAfterAccess(expire)
    .removalListener(disposeModel)
    .build[ModelName, Model[_]]()

  def disposeModel(key: ModelName, model: Model[_], reason: RemovalCause): Unit = {
    logger.info(s"removing model $key due to $reason")
    model.close()
  }
}
