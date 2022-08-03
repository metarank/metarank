package ai.metarank.fstore.redis

import ai.metarank.fstore.Persistence
import ai.metarank.fstore.redis.client.RedisPipeline.RedisOp
import ai.metarank.fstore.redis.client.RedisReader
import ai.metarank.model.Schema
import cats.effect.IO
import cats.effect.std.Queue

case class RedisPersistence(schema: Schema, queue: Queue[IO, RedisOp], client: RedisReader) extends Persistence {
  override lazy val counters = schema.counters.view.mapValues(RedisCounterFeature(_, queue, client)).toMap
  override lazy val periodicCounters =
    schema.periodicCounters.view.mapValues(RedisPeriodicCounterFeature(_, queue, client)).toMap
  override lazy val lists =
    schema.lists.view.mapValues(RedisBoundedListFeature(_, queue, client)).toMap
  override lazy val freqs   = schema.freqs.view.mapValues(RedisFreqEstimatorFeature(_, queue, client)).toMap
  override lazy val scalars = schema.scalars.view.mapValues(RedisScalarFeature(_, queue, client)).toMap
  override lazy val stats   = schema.stats.view.mapValues(RedisStatsEstimatorFeature(_, queue, client)).toMap
  override lazy val maps    = schema.maps.view.mapValues(RedisMapFeature(_, queue, client)).toMap

  override def kvstore[V: Persistence.KVCodec](name: String): Persistence.KVStore[V] =
    RedisKVStore(name, queue, client)
}
