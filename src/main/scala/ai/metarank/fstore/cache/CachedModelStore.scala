package ai.metarank.fstore.cache

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.{EventTicker, Persistence}
import ai.metarank.fstore.Persistence.{ModelName, ModelStore}
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.blemale.scaffeine.Scaffeine

import java.util.concurrent.ArrayBlockingQueue
import scala.concurrent.duration.*

case class CachedModelStore(fast: ModelStore, slow: ModelStore) extends ModelStore {
  override def put(value: Model[?]): IO[Unit] = fast.put(value) *> slow.put(value)

  override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
      key: Persistence.ModelName,
      pred: Predictor[C, T, M]
  ): IO[Option[M]] =
    fast.get(key, pred).flatMap {
      case Some(c) if !c.isClosed() => IO.pure(Some(c))
      case _ =>
        slow.get(key, pred).flatMap {
          case Some(model) => fast.put(model) *> IO.pure(Some(model))
          case None        => IO.pure(None)
        }
    }
}

object CachedModelStore extends Logging {
  val retiredModelsSize = 4

  def createCache(ticker: EventTicker, size: Int = 32, expire: FiniteDuration = 1.hour) = {
    val retired = new RetiredModels(retiredModelsSize)
    Scaffeine()
      .ticker(ticker)
      .maximumSize(size)
      .expireAfterAccess(expire)
      .removalListener(disposeModel(retired))
      .build[ModelName, Model[?]]()
  }

  def disposeModel(retired: RetiredModels)(key: ModelName, model: Model[?], reason: RemovalCause): Unit = reason match {
    // Closing frees native memory an in-flight predict may still be reading
    case RemovalCause.REPLACED =>
      logger.debug(s"model $key was replaced, parking the previous instance")
      retired.park(model)

    case _ =>
      logger.info(s"removing model $key due to $reason")
      model.close()
  }

  // A parked model is closed after `capacity` further replacements, bounding retained native memory
  class RetiredModels(capacity: Int) extends Logging {
    private val parked = new ArrayBlockingQueue[Model[?]](capacity)

    def park(model: Model[?]): Unit =
      while (!parked.offer(model)) Option(parked.poll()).foreach(close)

    def size(): Int = parked.size()

    private def close(model: Model[?]): Unit =
      try model.close()
      catch { case e: Throwable => logger.warn(s"failed to close a parked model", e) }
  }
}
