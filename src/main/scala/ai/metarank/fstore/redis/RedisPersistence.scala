package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, DBConfig, PipelineConfig}
import ai.metarank.fstore.cache.CachedFeature.{
  CachedBoundedListFeature,
  CachedCounterFeature,
  CachedFreqEstimatorFeature,
  CachedMapFeature,
  CachedPeriodicCounterFeature,
  CachedScalarFeature,
  CachedStatsEstimatorFeature
}
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.{KVCodec, ModelName}
import ai.metarank.fstore.cache.{CachedClickthroughStore, CachedKVStore}
import ai.metarank.fstore.memory.{
  MemBoundedList,
  MemClickthroughStore,
  MemCounter,
  MemFreqEstimator,
  MemKVStore,
  MemMapFeature,
  MemPeriodicCounter,
  MemScalarFeature,
  MemStatsEstimator
}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.FeatureValue.FrequencyValue
import ai.metarank.model.Write.PutFreqSample
import ai.metarank.model.{ClickthroughValues, FeatureKey, FeatureValue, Key, Scalar, Schema, Scope, Timestamp}
import ai.metarank.rank.Model.Scorer
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.kernel.Resource
import com.github.blemale.scaffeine.Scaffeine
import io.lettuce.core.TrackingArgs
import io.lettuce.core.api.push.{PushListener, PushMessage}

import java.nio.ByteBuffer
import java.util
import scala.jdk.CollectionConverters._
import shapeless.syntax.typeable._
import scala.concurrent.duration._
import java.util.concurrent.CompletableFuture

case class RedisPersistence(
    schema: Schema,
    stateClient: RedisClient,
    modelClient: RedisClient,
    valuesClient: RedisClient,
    rankingsClient: RedisClient,
    cache: CacheConfig
) extends Persistence
    with Logging {
  import RedisPersistence._

  stateClient.readerConn.addListener(new PushListener {
    override def onPushMessage(message: PushMessage): Unit = if (message.getType == "invalidate") {
      val content = message.getContent()
      if (content.size() >= 2) {
        val payloads = content.get(1).asInstanceOf[util.ArrayList[ByteBuffer]]
        if (!payloads.isEmpty) {
          payloads.asScala.foreach(bytes => {
            val keyRaw    = new String(bytes.array())
            val keyString = keyRaw.substring(2)
            val keyType   = keyRaw.substring(0, 1)
            invalidate(keyType, keyString)
            logger.debug(s"cache invalidation message: key=$keyString type=$keyType")
          })
        } else {
          logger.debug("empty invalidation message")
        }
      }

    }

    def invalidate(keyType: String, keyString: String) = keyType match {
      case Prefix.STATE =>
        Key.fromString(keyString).foreach { key =>
          schema.configs.get(FeatureKey(key)) match {
            case Some(_: ScalarConfig)          => scalarCache.invalidate(key)
            case Some(_: CounterConfig)         => counterCache.invalidate(key)
            case Some(_: BoundedListConfig)     => boundedListCache.invalidate(key)
            case Some(_: PeriodicCounterConfig) => periodicCounterCache.invalidate(key)
            case Some(_: FreqEstimatorConfig)   => freqCache.invalidate(key)
            case Some(_: StatsEstimatorConfig)  => statCache.invalidate(key)
            case Some(_: MapConfig)             => mapCache.invalidate(key)
            case None                           => logger.warn(s"cannot invalidate caches for key $key")
          }
        }
      case Prefix.VALUES => Key.fromString(keyString).foreach(valueCache.invalidate)
      case Prefix.MODELS => modelCache.invalidate(ModelName(keyString))
      case Prefix.CT     => // no caching
      case _             => logger.warn(s"cannot handle invalidation of key=${keyString}")
    }

  })

  val defaultCacheBuilder       = Scaffeine().maximumSize(cache.maxSize).expireAfterAccess(cache.ttl)
  lazy val scalarCache          = defaultCacheBuilder.build[Key, Scalar]()
  lazy val counterCache         = defaultCacheBuilder.build[Key, Long]()
  lazy val boundedListCache     = defaultCacheBuilder.build[Key, List[TimeValue]]()
  lazy val periodicCounterCache = defaultCacheBuilder.build[Key, Map[Timestamp, Long]]()
  lazy val freqCache            = defaultCacheBuilder.build[Key, List[String]]()
  lazy val statCache            = defaultCacheBuilder.build[Key, List[Double]]()
  lazy val mapCache             = defaultCacheBuilder.build[Key, Map[String, Scalar]]()
  lazy val valueCache           = defaultCacheBuilder.build[Key, FeatureValue]()
  lazy val modelCache           = defaultCacheBuilder.build[ModelName, Scorer]()

  override lazy val lists = schema.lists.map { case (name, conf) =>
    name -> CachedBoundedListFeature(
      fast = MemBoundedList(conf, boundedListCache),
      slow = RedisBoundedListFeature(conf, stateClient, Prefix.STATE)
    )
  }

  override lazy val counters = schema.counters.map { case (name, conf) =>
    name -> CachedCounterFeature(
      fast = MemCounter(conf, counterCache),
      slow = RedisCounterFeature(conf, stateClient, Prefix.STATE)
    )
  }
  override lazy val periodicCounters =
    schema.periodicCounters.map { case (name, conf) =>
      name -> CachedPeriodicCounterFeature(
        fast = MemPeriodicCounter(conf, periodicCounterCache),
        slow = RedisPeriodicCounterFeature(conf, stateClient, Prefix.STATE)
      )
    }

  override lazy val freqs = schema.freqs.map { case (name, conf) =>
    name -> CachedFreqEstimatorFeature(
      fast = MemFreqEstimator(conf, freqCache),
      slow = RedisFreqEstimatorFeature(conf, stateClient, Prefix.STATE)
    )
  }

  override lazy val scalars = schema.scalars.map { case (name, conf) =>
    name -> CachedScalarFeature(
      fast = MemScalarFeature(conf, scalarCache),
      slow = RedisScalarFeature(conf, stateClient, Prefix.STATE)
    )
  }
  override lazy val stats = schema.stats.map { case (name, conf) =>
    name -> CachedStatsEstimatorFeature(
      fast = MemStatsEstimator(conf, statCache),
      slow = RedisStatsEstimatorFeature(conf, stateClient, Prefix.STATE)
    )
  }

  override lazy val maps = schema.maps.map { case (name, conf) =>
    name -> CachedMapFeature(
      fast = MemMapFeature(conf, mapCache),
      slow = RedisMapFeature(conf, stateClient, Prefix.STATE)
    )
  }

  import ai.metarank.rank.Model._
  override lazy val models: Persistence.KVStore[ModelName, Scorer] = CachedKVStore(
    fast = MemKVStore(modelCache),
    slow = RedisKVStore(modelClient, Prefix.MODELS)(KVCodec.modelKeyCodec, KVCodec.jsonCodec)
  )

  override lazy val values: Persistence.KVStore[Key, FeatureValue] = CachedKVStore(
    fast = MemKVStore(valueCache),
    slow = RedisKVStore(valuesClient, Prefix.VALUES)
  )

  override lazy val cts: Persistence.ClickthroughStore = RedisClickthroughStore(rankingsClient, Prefix.CT)

  override def healthcheck(): IO[Unit] =
    stateClient.ping().void

  override def sync: IO[Unit] = for {
    _ <- info("flushing redis pipeline")
    _ <- stateClient.doFlush(CompletableFuture.completedFuture(0))
    _ <- valuesClient.doFlush(CompletableFuture.completedFuture(0))
    _ <- rankingsClient.doFlush(CompletableFuture.completedFuture(0))
    _ <- IO.sleep(1.second)
  } yield {
    logger.info("redis pipeline flushed")
  }
}

object RedisPersistence {
  object Prefix {
    val STATE  = "s"
    val VALUES = "v"
    val MODELS = "m"
    val CT     = "c"
  }
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
    _ <- Resource.liftK(
      IO.fromCompletableFuture(
        IO(
          state.reader
            .clientTracking(
              TrackingArgs.Builder
                .enabled()
                .bcast()
                .noloop()
                .prefixes(Prefix.STATE, Prefix.VALUES, Prefix.MODELS, Prefix.CT)
            )
            .toCompletableFuture
        )
      )
    )
  } yield {
    RedisPersistence(schema, state, models, values, rankings, cache)
  }

}
