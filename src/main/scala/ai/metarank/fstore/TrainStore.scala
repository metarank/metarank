package ai.metarank.fstore

import ai.metarank.config.TrainConfig.{
  DiscardTrainConfig,
  FileTrainConfig,
  MemoryTrainConfig,
  RedisTrainConfig,
  S3TrainConfig
}
import ai.metarank.config.{StateStoreConfig, TrainConfig}
import ai.metarank.fstore.clickthrough.{DiscardTrainStore, FileTrainStore, S3TrainStore}
import ai.metarank.fstore.memory.MemTrainStore
import ai.metarank.fstore.redis.{RedisPersistence, RedisTrainStore}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.TrainValues
import cats.effect.IO
import cats.effect.kernel.Resource
import scala.concurrent.duration._

trait TrainStore {
  def put(cts: List[TrainValues]): IO[Unit]
  def flush(): IO[Unit]
  def getall(): fs2.Stream[IO, TrainValues]
}

object TrainStore {
  def fromConfig(conf: TrainConfig): Resource[IO, TrainStore] = conf match {
    case c: DiscardTrainConfig => Resource.pure(DiscardTrainStore)
    case c: S3TrainConfig      => S3TrainStore.create(c)
    case c: FileTrainConfig    => FileTrainStore.create(c.path, c.format)
    case c: RedisTrainConfig =>
      for {
        rankings <- RedisClient.create(c.host.value, c.port.value, c.db, c.pipeline, c.auth, c.tls, c.timeout)
      } yield {
        RedisTrainStore(rankings, RedisPersistence.Prefix.CT, c.format, 365.days)
      }
    case _: MemoryTrainConfig => Resource.pure(MemTrainStore())
    case other => Resource.raiseError[IO, TrainStore, Throwable](new Exception(s"conf $other is not supported"))
  }
}
