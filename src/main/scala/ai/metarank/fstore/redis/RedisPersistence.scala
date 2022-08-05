package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisStateConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.{RedisPipeline, RedisClient}
import ai.metarank.model.Schema
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import scala.concurrent.duration._

case class RedisPersistence(schema: Schema, client: RedisClient)
    extends Persistence {
  override lazy val counters = schema.counters.view.mapValues(RedisCounterFeature(_, client)).toMap
  override lazy val periodicCounters =
    schema.periodicCounters.view.mapValues(RedisPeriodicCounterFeature(_, client)).toMap
  override lazy val lists =
    schema.lists.view.mapValues(RedisBoundedListFeature(_, client)).toMap
  override lazy val freqs   = schema.freqs.view.mapValues(RedisFreqEstimatorFeature(_, client)).toMap
  override lazy val scalars = schema.scalars.view.mapValues(RedisScalarFeature(_, client)).toMap
  override lazy val stats   = schema.stats.view.mapValues(RedisStatsEstimatorFeature(_, client)).toMap
  override lazy val maps    = schema.maps.view.mapValues(RedisMapFeature(_, client)).toMap

  override def kv[K: KVCodec, V: KVCodec](name: String): Persistence.KVStore[K, V] =
    RedisKVStore(name, client)

  override def stream[V: Persistence.KVCodec](name: String): Persistence.StreamStore[V] =
    RedisStreamStore(name, client)

  override def healthcheck(): IO[Unit] =
    client.ping().void

}

object RedisPersistence {
  def create(schema: Schema, host: String, port: Int): Resource[IO, RedisPersistence] = for {
    client <- RedisClient.create(host, port, 0)
  } yield {
    RedisPersistence(schema, client)
  }

}
