package ai.metarank.fstore

import ai.metarank.config.CoreConfig.{ImportCacheConfig, ImportConfig}
import ai.metarank.config.{ModelConfig, StateStoreConfig}
import ai.metarank.fstore.Persistence.{KVStore, ModelName, ModelStore}
import ai.metarank.config.StateStoreConfig.FileStateConfig
import ai.metarank.fstore.codec.impl.ScopeCodec
import ai.metarank.fstore.file.FilePersistence
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.RedisPersistence
import ai.metarank.ml.{Context, Model, Predictor}
import ai.metarank.model.Feature.{
  BoundedListFeature,
  CounterFeature,
  FreqEstimatorFeature,
  MapFeature,
  PeriodicCounterFeature,
  ScalarFeature,
  StatsEstimatorFeature
}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{FeatureKey, FeatureValue, Key, Schema, Scope}
import ai.metarank.util.Logging
import cats.effect.{IO, Resource}
import io.circe.Codec

trait Persistence {
  lazy val ticker = new EventTicker

  def schema: Schema
  def counters: Map[FeatureKey, CounterFeature]
  def periodicCounters: Map[FeatureKey, PeriodicCounterFeature]
  def lists: Map[FeatureKey, BoundedListFeature]
  def freqs: Map[FeatureKey, FreqEstimatorFeature]
  def scalars: Map[FeatureKey, ScalarFeature]
  def stats: Map[FeatureKey, StatsEstimatorFeature]
  def maps: Map[FeatureKey, MapFeature]

  def values: KVStore[Key, FeatureValue]
  def models: ModelStore
  def healthcheck(): IO[Unit]
  def sync: IO[Unit]
}

object Persistence extends Logging {
  case class ModelName(name: String)

  trait KVCodec[T] {
    def encode(value: T): Array[Byte]
    def decode(bytes: Array[Byte]): Either[Throwable, T]
  }

  object KVCodec {
    import io.circe.syntax._
    import io.circe.parser.{decode => cdecode}
    implicit def jsonCodec[T](implicit codec: Codec[T]): KVCodec[T] = new KVCodec[T] {
      override def encode(value: T)                                 = value.asJson.noSpaces.getBytes()
      override def decode(bytes: Array[Byte]): Either[Throwable, T] = cdecode[T](new String(bytes))
    }

    implicit val stringCodec: KVCodec[String] = new KVCodec[String] {
      override def decode(bytes: Array[Byte]): Either[Throwable, String] = Right(new String(bytes))
      override def encode(value: String): Array[Byte]                    = value.getBytes()
    }

    implicit val keyCodec: KVCodec[Key] = new KVCodec[Key] {
      override def decode(bytes: Array[Byte]): Either[Throwable, Key] = {
        val str = new String(bytes)
        str.split("/").toList match {
          case scope :: name :: Nil => ScopeCodec.decode(scope).map(s => Key(s, FeatureName(name)))
          case _                    => Left(new Exception(s"cannot decode key $str"))
        }
      }

      override def encode(value: Key): Array[Byte] = s"${value.scope.asString}/${value.feature.value}".getBytes()
    }

    implicit val modelKeyCodec: KVCodec[ModelName] = new KVCodec[ModelName] {
      override def encode(value: ModelName) = value.name.getBytes()

      override def decode(bytes: Array[Byte]): Either[Throwable, ModelName] = Right(ModelName(new String(bytes)))
    }
  }

  trait KVStore[K, V] {
    def put(values: Map[K, V]): IO[Unit]
    def get(keys: List[K]): IO[Map[K, V]]
    def get(key: K): IO[Option[V]] = get(List(key)).map(_.get(key))
  }

  trait ModelStore {
    def put(value: Model[_]): IO[Unit]
    def get[C <: ModelConfig, T <: Context, M <: Model[T]](key: ModelName, pred: Predictor[C, T, M]): IO[Option[M]]
  }

  object KVStore {
    def empty[K, V] = new KVStore[K, V] {
      override def get(keys: List[K]): IO[Map[K, V]] = IO.pure(Map.empty)
      override def put(values: Map[K, V]): IO[Unit]  = IO.unit
    }
  }

  def fromConfig(schema: Schema, conf: StateStoreConfig, imp: ImportCacheConfig): Resource[IO, Persistence] =
    conf match {
      case StateStoreConfig.RedisStateConfig(host, port, db, cache, pipeline, fmt, auth, tls, timeout) =>
        RedisPersistence.create(schema, host.value, port.value, db, cache, pipeline, fmt, auth, tls, timeout)
      case f: FileStateConfig =>
        FilePersistence.create(f, schema, imp)
      case StateStoreConfig.MemoryStateConfig() =>
        Resource.make(
          info("using in-memory persistence")
            .flatMap(_ => warn("in-memory persistence IS NOT FOR PRODUCTION, you will lose all the state upon restart"))
            .flatMap(_ => IO(MemPersistence(schema)))
        )(_ => IO.unit)
    }
}
