package ai.metarank.fstore

import ai.metarank.config.StateStoreConfig
import ai.metarank.fstore.Persistence.{KVCodec, KVStore, ModelKey, StreamStore}
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.RedisPersistence
import ai.metarank.model.Feature.{
  BoundedList,
  Counter,
  FreqEstimator,
  MapFeature,
  PeriodicCounter,
  ScalarFeature,
  StatsEstimator
}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Env, FeatureKey, FeatureValue, Key, Schema, Scope}
import cats.effect.{IO, Resource}
import io.circe.Codec

trait Persistence {

  def schema: Schema
  def counters: Map[FeatureKey, Counter]
  def periodicCounters: Map[FeatureKey, PeriodicCounter]
  def lists: Map[FeatureKey, BoundedList]
  def freqs: Map[FeatureKey, FreqEstimator]
  def scalars: Map[FeatureKey, ScalarFeature]
  def stats: Map[FeatureKey, StatsEstimator]
  def maps: Map[FeatureKey, MapFeature]

  lazy val state: KVStore[Key, FeatureValue] = kv[Key, FeatureValue]("state")(KVCodec.keyCodec, KVCodec.jsonCodec)
  lazy val models: KVStore[ModelKey, String] = kv[ModelKey, String]("model")

  protected def kv[K: KVCodec, V: KVCodec](name: String): KVStore[K, V]
  protected def stream[V: KVCodec](name: String): StreamStore[V]
  def healthcheck(): IO[Unit]
}

object Persistence {
  case class ModelKey(env: Env, name: String)
  trait KVCodec[T] {
    def encode(value: T): String
    def decode(str: String): Either[Throwable, T]
  }

  object KVCodec {
    import io.circe.syntax._
    import io.circe.parser.{decode => cdecode}
    implicit def jsonCodec[T](implicit codec: Codec[T]) = new KVCodec[T] {
      override def encode(value: T): String                  = value.asJson.noSpaces
      override def decode(str: String): Either[Throwable, T] = cdecode[T](str)
    }
    implicit val stringCodec: KVCodec[String] = new KVCodec[String] {
      override def decode(str: String): Either[Throwable, String] = Right(str)
      override def encode(value: String): String                  = value
    }
    implicit val keyCodec: KVCodec[Key] = new KVCodec[Key] {
      override def decode(str: String): Either[Throwable, Key] = {
        str.split("/").toList match {
          case scope :: name :: Nil => Scope.fromString(scope).map(s => Key(s, FeatureName(name)))
          case _                    => Left(new Exception(s"cannot decode key $str"))
        }
      }

      override def encode(value: Key): String = value.asString
    }
    implicit val modelKeyCodec: KVCodec[ModelKey] = new KVCodec[ModelKey] {
      override def encode(value: ModelKey): String = s"${value.env.value}/${value.name}"

      override def decode(str: String): Either[Throwable, ModelKey] = str.split("/").toList match {
        case env :: name :: Nil => Right(ModelKey(Env(env), name))
        case _                  => Left(new Exception(s"cannot decode model key $str"))
      }
    }
  }

  trait KVStore[K, V] {
    def put(values: Map[K, V]): IO[Unit]
    def get(keys: List[K]): IO[Map[K, V]]
  }

  trait StreamStore[V] {
    def push(values: List[V]): IO[Unit]
    def getall(): fs2.Stream[IO, V]
  }

  def fromConfig(schema: Schema, conf: StateStoreConfig) = conf match {
    case StateStoreConfig.RedisStateConfig(host, port) => RedisPersistence.create(schema, host.value, port.value)
    case StateStoreConfig.MemoryStateConfig()          => Resource.make(IO(MemPersistence(schema)))(_ => IO.unit)
  }

  def blackhole() = new Persistence {
    override def schema: Schema                                     = Schema(Nil)
    override def counters: Map[FeatureKey, Counter]                 = Map.empty
    override def periodicCounters: Map[FeatureKey, PeriodicCounter] = Map.empty
    override def lists: Map[FeatureKey, BoundedList]                = Map.empty
    override def freqs: Map[FeatureKey, FreqEstimator]              = Map.empty
    override def scalars: Map[FeatureKey, ScalarFeature]            = Map.empty
    override def stats: Map[FeatureKey, StatsEstimator]             = Map.empty
    override def maps: Map[FeatureKey, MapFeature]                  = Map.empty

    override protected def kv[K: KVCodec, V: KVCodec](name: String): KVStore[K, V] = new KVStore[K, V] {
      override def put(values: Map[K, V]): IO[Unit]  = IO.unit
      override def get(keys: List[K]): IO[Map[K, V]] = IO.pure(Map.empty)
    }
    override protected def stream[V: KVCodec](name: String): StreamStore[V] = new StreamStore[V] {
      override def push(values: List[V]): IO[Unit] = IO.unit
      override def getall(): fs2.Stream[IO, V]     = fs2.Stream.empty
    }

    override def healthcheck(): IO[Unit] = IO.unit
  }

}
