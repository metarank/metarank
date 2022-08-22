package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, DBConfig, PipelineConfig}
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.KVCodec
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.{FeatureValue, Key, Schema}
import cats.effect.IO
import cats.effect.kernel.Resource

case class RedisPersistence(
    schema: Schema,
    stateClient: RedisClient,
    modelClient: RedisClient,
    valuesClient: RedisClient,
    rankingsClient: RedisClient,
    intsClient: RedisClient,
    cache: CacheConfig
) extends Persistence {
  override lazy val counters = schema.counters.view.mapValues(RedisCounterFeature(_, stateClient)).toMap
  override lazy val periodicCounters =
    schema.periodicCounters.view.mapValues(RedisPeriodicCounterFeature(_, stateClient)).toMap
  override lazy val lists =
    schema.lists.view.mapValues(RedisBoundedListFeature(_, stateClient)).toMap
  override lazy val freqs   = schema.freqs.view.mapValues(RedisFreqEstimatorFeature(_, stateClient)).toMap
  override lazy val scalars = schema.scalars.view.mapValues(RedisScalarFeature(_, stateClient)).toMap
  override lazy val stats   = schema.stats.view.mapValues(RedisStatsEstimatorFeature(_, stateClient)).toMap
  override lazy val maps    = schema.maps.view.mapValues(RedisMapFeature(_, stateClient)).toMap

  import ai.metarank.rank.Model._
  override lazy val models: Persistence.KVStore[Persistence.ModelName, Scorer] =
    RedisKVStore(modelClient, cache)(KVCodec.modelKeyCodec, KVCodec.jsonCodec)

  override lazy val values: Persistence.KVStore[Key, FeatureValue] = RedisKVStore(valuesClient, cache)

  override lazy val cts: Persistence.ClickthroughStore = RedisClickthroughStore(rankingsClient, intsClient)

  override def healthcheck(): IO[Unit] =
    stateClient.ping().void

}

object RedisPersistence {
  def create(
      schema: Schema,
      host: String,
      port: Int,
      db: DBConfig,
      cache: CacheConfig,
      pipeline: PipelineConfig
  ): Resource[IO, RedisPersistence] = for {
    state    <- RedisClient.create(host, port, db.state, pipeline)
    models   <- RedisClient.create(host, port, db.models, pipeline)
    values   <- RedisClient.create(host, port, db.values, pipeline)
    rankings <- RedisClient.create(host, port, db.rankings, pipeline)
    clicks   <- RedisClient.create(host, port, db.hist, pipeline)
  } yield {
    RedisPersistence(schema, state, models, values, rankings, clicks, cache)
  }

}
