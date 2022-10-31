package ai.metarank.fstore

import ai.metarank.config.StateStoreConfig.{MemoryStateConfig, RedisStateConfig}
import ai.metarank.config.TrainConfig.{FileTrainConfig, MemoryTrainConfig, RedisTrainConfig, S3TrainConfig}
import ai.metarank.config.{StateStoreConfig, TrainConfig}
import ai.metarank.fstore.clickthrough.FileClickthroughStore
import ai.metarank.fstore.memory.MemClickthroughStore
import ai.metarank.fstore.redis.{RedisClickthroughStore, RedisPersistence}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.ClickthroughValues
import cats.effect.IO
import cats.effect.kernel.Resource

trait ClickthroughStore {
  def put(cts: List[ClickthroughValues]): IO[Unit]
  def flush(): IO[Unit]
  def getall(): fs2.Stream[IO, ClickthroughValues]
}

object ClickthroughStore {
  def fromConfig(conf: TrainConfig): Resource[IO, ClickthroughStore] = conf match {
    case c: FileTrainConfig => FileClickthroughStore.create(c.path, c.format)
    case c: RedisTrainConfig =>
      for {
        rankings <- RedisClient.create(c.host.value, c.port.value, c.db, c.pipeline, c.auth)
      } yield {
        RedisClickthroughStore(rankings, RedisPersistence.Prefix.CT, c.format)
      }
    case _: MemoryTrainConfig => Resource.pure(MemClickthroughStore())
    case other => Resource.raiseError[IO, ClickthroughStore, Throwable](new Exception(s"conf $other is not supported"))
  }
}
