package ai.metarank.fstore.memory

import ai.metarank.config.StateStoreConfig.RedisStateConfig.CacheConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.{KVCodec, ModelName}
import ai.metarank.fstore.memory.MemPersistence.FeatureStateExpiry
import ai.metarank.model.{FeatureKey, FeatureValue, Key, Schema}
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.benmanes.caffeine.cache.{Caffeine, Expiry}
import com.github.blemale.scaffeine.Scaffeine

case class MemPersistence(schema: Schema) extends Persistence {
  val cache =
    Scaffeine(Caffeine.newBuilder().ticker(ticker).expireAfter(FeatureStateExpiry(schema))).build[Key, AnyRef]()

  override lazy val counters         = schema.counters.view.mapValues(MemCounter(_, cache)).toMap
  override lazy val periodicCounters = schema.periodicCounters.view.mapValues(MemPeriodicCounter(_, cache)).toMap
  override lazy val lists            = schema.lists.view.mapValues(MemBoundedList(_, cache)).toMap
  override lazy val freqs            = schema.freqs.view.mapValues(MemFreqEstimator(_, cache)).toMap
  override lazy val scalars          = schema.scalars.view.mapValues(MemScalarFeature(_, cache)).toMap
  override lazy val stats            = schema.stats.view.mapValues(MemStatsEstimator(_, cache)).toMap
  override lazy val maps             = schema.maps.view.mapValues(MemMapFeature(_, cache)).toMap

  override lazy val models: Persistence.KVStore[ModelName, Scorer] = MemModelStore()
  override lazy val values: Persistence.KVStore[Key, FeatureValue] = MemKVStore()

  override lazy val cts: Persistence.ClickthroughStore = MemClickthroughStore()
  override def healthcheck(): IO[Unit]                 = IO.unit
  override def sync: IO[Unit]                          = IO.unit

}

object MemPersistence {
  case class FeatureStateExpiry(schema: Schema) extends Expiry[Key, AnyRef] with Logging {
    override def expireAfterCreate(key: Key, value: scala.AnyRef, currentTime: Long): Long =
      expiration(key, currentTime, Long.MaxValue)
    override def expireAfterUpdate(key: Key, value: scala.AnyRef, currentTime: Long, currentDuration: Long): Long =
      expiration(key, currentTime, currentDuration)

    override def expireAfterRead(key: Key, value: scala.AnyRef, currentTime: Long, currentDuration: Long): Long =
      currentDuration

    def expiration(key: Key, currentTime: Long, currentDuration: Long): Long =
      schema.configs.get(FeatureKey(key)) match {
        case Some(conf) => currentTime + conf.ttl.toNanos
        case None =>
          logger.warn(s"cannot compute expiration for key $key")
          currentDuration
      }
  }
}
