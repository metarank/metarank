package ai.metarank.fstore.cached

import ai.metarank.config.ModelConfig
import ai.metarank.fstore.{EventTicker, Persistence}
import ai.metarank.fstore.Persistence.{ModelName, ModelStore}
import ai.metarank.fstore.cache.CachedModelStore
import ai.metarank.fstore.cached.CachedModelStoreTest.AlwaysAllocateModelStore
import ai.metarank.fstore.memory.MemModelStore
import ai.metarank.fstore.redis.RedisModelStore
import ai.metarank.fstore.redis.RedisPersistence.Prefix
import ai.metarank.ml.Model.RankModel
import ai.metarank.ml.rank.QueryRequest
import ai.metarank.ml.rank.ShuffleRanker.{ShuffleConfig, ShuffleModel}
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.model.Timestamp
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class CachedModelStoreTest extends AnyFlatSpec with Matchers {
  lazy val ticker                   = new EventTicker
  val cache                         = CachedModelStore.createCache(ticker, 1, 1.millis)
  lazy val alwaysAllocateModelStore = AlwaysAllocateModelStore()
  lazy val store = CachedModelStore(
    fast = MemModelStore(cache),
    slow = alwaysAllocateModelStore
  )
  it should "not leak native mem" in {
    for {
      i <- 0 until 100
    } yield {
      ticker.tick(Timestamp.now)
      Thread.sleep(1L)
      store.get(ModelName("aaa"), null).unsafeRunSync()
    }
    alwaysAllocateModelStore.allocated shouldBe 1
  }
}

object CachedModelStoreTest {
  case class NativeMemModel(store: AlwaysAllocateModelStore) extends RankModel {
    override def name: String                                       = "aaa"
    override def predict(request: QueryRequest): IO[Model.Response] = ???
    override def save(): Option[Array[Byte]]                        = ???
    override def close(): Unit = {
      store.allocated -= 1
    }
  }
  case class AlwaysAllocateModelStore(var allocated: Int = 0) extends ModelStore {
    override def put(value: Model[_]): IO[Unit] = ???
    override def get[C <: ModelConfig, T <: Context, M <: Model[T]](
        key: Persistence.ModelName,
        pred: Predictor[C, T, M]
    ): IO[Option[M]] = {
      val br = 1
      IO {
        allocated += 1
        Some(NativeMemModel(this).asInstanceOf[M])
      }
    }
  }
}
