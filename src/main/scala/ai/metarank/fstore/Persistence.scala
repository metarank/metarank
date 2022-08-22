package ai.metarank.fstore

import ai.metarank.config.StateStoreConfig
import ai.metarank.fstore.Persistence.{ClickthroughStore, KVCodec, KVStore, ModelName}
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.fstore.redis.RedisPersistence
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.Feature.{
  BoundedList,
  Counter,
  FreqEstimator,
  MapFeature,
  PeriodicCounter,
  ScalarFeature,
  StatsEstimator
}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{
  Clickthrough,
  ClickthroughValues,
  EventId,
  FeatureKey,
  FeatureValue,
  ItemValue,
  Key,
  MValue,
  Schema,
  Scope
}
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.Logging
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

  def values: KVStore[Key, FeatureValue]
  def models: KVStore[ModelName, Scorer]
  def cts: ClickthroughStore
  def healthcheck(): IO[Unit]
}

object Persistence extends Logging {
  case class ModelName(name: String) extends AnyVal
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
    implicit val modelKeyCodec: KVCodec[ModelName] = new KVCodec[ModelName] {
      override def encode(value: ModelName): String = value.name

      override def decode(str: String): Either[Throwable, ModelName] = Right(ModelName(str))
    }
  }

  trait KVStore[K, V] {
    def put(values: Map[K, V]): IO[Unit]
    def get(keys: List[K]): IO[Map[K, V]]
  }

  object KVStore {
    def empty[K, V] = new KVStore[K, V] {
      override def get(keys: List[K]): IO[Map[K, V]] = IO.pure(Map.empty)
      override def put(values: Map[K, V]): IO[Unit]  = IO.unit
    }
  }

  trait ClickthroughStore {
    def putRanking(ranking: RankingEvent): IO[Unit]
    def putValues(id: EventId, values: List[ItemValue]): IO[Unit]
    def putInteraction(id: EventId, item: ItemId, tpe: String): IO[Unit]
    def getClickthrough(id: EventId): IO[Option[Clickthrough]]
    def getall(): fs2.Stream[IO, ClickthroughValues]
  }

  def fromConfig(schema: Schema, conf: StateStoreConfig): Resource[IO, Persistence] = conf match {
    case StateStoreConfig.RedisStateConfig(host, port, db, cache) =>
      RedisPersistence.create(schema, host.value, port.value, db, cache)
    case StateStoreConfig.MemoryStateConfig() =>
      Resource.make(info("using in-memory persistence") *> IO(MemPersistence(schema)))(_ => IO.unit)
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

    override lazy val cts: ClickthroughStore             = ???
    override lazy val models: KVStore[ModelName, Scorer] = KVStore.empty
    override lazy val values: KVStore[Key, FeatureValue] = KVStore.empty
    override def healthcheck(): IO[Unit]                 = IO.unit
  }

}
