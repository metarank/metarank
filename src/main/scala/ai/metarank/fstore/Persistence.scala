package ai.metarank.fstore

import ai.metarank.fstore.Persistence.{KVCodec, KVStore}
import ai.metarank.model.Feature.{
  BoundedList,
  Counter,
  FreqEstimator,
  MapFeature,
  PeriodicCounter,
  ScalarFeature,
  StatsEstimator
}
import ai.metarank.model.{FeatureKey, Schema}
import cats.effect.IO
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

  def kvstore[V: KVCodec](name: String): KVStore[V]
}

object Persistence {
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
  }

  trait KVStore[V] {
    def put(values: Map[String, V]): IO[Unit]
    def get(keys: List[String]): IO[Map[String, V]]
  }
}
