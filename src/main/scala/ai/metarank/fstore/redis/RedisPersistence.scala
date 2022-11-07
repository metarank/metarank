package ai.metarank.fstore.redis

import ai.metarank.config.StateStoreConfig.RedisCredentials
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
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.memory.{
  MemBoundedList,
  MemCounter,
  MemFreqEstimator,
  MemKVStore,
  MemMapFeature,
  MemPeriodicCounter,
  MemScalarFeature,
  MemStatsEstimator
}
import ai.metarank.fstore.redis.client.RedisClient
import ai.metarank.model.{FeatureValue, Key, Schema}
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

case class RedisPersistence(
    schema: Schema,
    stateClient: RedisClient,
    modelClient: RedisClient,
    valuesClient: RedisClient,
//    rankingsClient: RedisClient,
    cache: CacheConfig,
    format: StoreFormat
) extends Persistence
    with Logging {
  import RedisPersistence._

  stateClient.readerConn.addListener(new PushListener {
    override def onPushMessage(message: PushMessage): Unit = if (message.getType == "invalidate") {
      val content = message.getContent()
      if (content.size() >= 2) {
        val payloads = content.get(1).asInstanceOf[util.ArrayList[ByteBuffer]]
        if ((payloads != null) && !payloads.isEmpty) {
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
      case Prefix.STATE  => Key.fromString(keyString).foreach(stateCache.invalidate)
      case Prefix.VALUES => // no caching
      case Prefix.MODELS => modelCache.invalidate(ModelName(keyString))
      case Prefix.CT     => // no caching
      case _             => logger.warn(s"cannot handle invalidation of key=${keyString}")
    }

  })

  lazy val stateCache = Scaffeine()
    .ticker(ticker)
    .maximumSize(cache.maxSize)
    .expireAfterAccess(cache.ttl)
    .build[Key, AnyRef]()

  lazy val modelCache = Scaffeine()
    .ticker(ticker)
    .maximumSize(32)
    .expireAfterAccess(1.hour)
    .build[ModelName, Scorer]()

  override lazy val lists = schema.lists.map { case (name, conf) =>
    name -> CachedBoundedListFeature(
      fast = MemBoundedList(conf, stateCache),
      slow = RedisBoundedListFeature(conf, stateClient, Prefix.STATE, format)
    )
  }

  override lazy val counters = schema.counters.map { case (name, conf) =>
    name -> CachedCounterFeature(
      fast = MemCounter(conf, stateCache),
      slow = RedisCounterFeature(conf, stateClient, Prefix.STATE, format)
    )
  }
  override lazy val periodicCounters =
    schema.periodicCounters.map { case (name, conf) =>
      name -> CachedPeriodicCounterFeature(
        fast = MemPeriodicCounter(conf, stateCache),
        slow = RedisPeriodicCounterFeature(conf, stateClient, Prefix.STATE, format)
      )
    }

  override lazy val freqs = schema.freqs.map { case (name, conf) =>
    name -> CachedFreqEstimatorFeature(
      fast = MemFreqEstimator(conf, stateCache),
      slow = RedisFreqEstimatorFeature(conf, stateClient, Prefix.STATE, format)
    )
  }

  override lazy val scalars = schema.scalars.map { case (name, conf) =>
    name -> CachedScalarFeature(
      fast = MemScalarFeature(conf, stateCache),
      slow = RedisScalarFeature(conf, stateClient, Prefix.STATE, format)
    )
  }
  override lazy val stats = schema.stats.map { case (name, conf) =>
    name -> CachedStatsEstimatorFeature(
      fast = MemStatsEstimator(conf, stateCache),
      slow = RedisStatsEstimatorFeature(conf, stateClient, Prefix.STATE, format)
    )
  }

  override lazy val maps = schema.maps.map { case (name, conf) =>
    name -> CachedMapFeature(
      fast = MemMapFeature(conf, stateCache),
      slow = RedisMapFeature(conf, stateClient, Prefix.STATE, format)
    )
  }

  import ai.metarank.rank.Model._
  override lazy val models: Persistence.KVStore[ModelName, Scorer] = CachedKVStore(
    fast = MemKVStore(modelCache),
    slow = RedisKVStore(modelClient, Prefix.MODELS)(format.model, format.scorer)
  )

  override lazy val values: Persistence.KVStore[Key, FeatureValue] =
    RedisKVStore(valuesClient, Prefix.VALUES)(format.key, format.featureValue)

//  override lazy val cts: Persistence.ClickthroughStore = RedisClickthroughStore(rankingsClient, Prefix.CT, format)

  override def healthcheck(): IO[Unit] =
    stateClient.ping().void

  override def sync: IO[Unit] = for {
    start <- IO(System.currentTimeMillis())
    _     <- stateClient.doFlush(stateClient.writer.ping().toCompletableFuture)
    _     <- valuesClient.doFlush(valuesClient.writer.ping().toCompletableFuture)
//    _     <- rankingsClient.doFlush(rankingsClient.writer.ping().toCompletableFuture)
    _ <- modelClient.doFlush(modelClient.writer.ping().toCompletableFuture)
  } yield {
    logger.info(s"redis pipeline flushed, took ${System.currentTimeMillis() - start}ms")
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
      pipeline: PipelineConfig,
      format: StoreFormat,
      auth: Option[RedisCredentials]
  ): Resource[IO, RedisPersistence] = for {
    state  <- RedisClient.create(host, port, db.state, pipeline, auth)
    models <- RedisClient.create(host, port, db.models, pipeline, auth)
    values <- RedisClient.create(host, port, db.values, pipeline, auth)
//    rankings <- RedisClient.create(host, port, db.rankings, pipeline, auth)
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
    RedisPersistence(schema, state, models, values, cache, format)
  }

}
