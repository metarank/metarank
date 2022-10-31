package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, DBConfig, PipelineConfig}
import ai.metarank.fstore.codec.StoreFormat
import ai.metarank.fstore.codec.StoreFormat.BinaryStoreFormat
import ai.metarank.util.Logging
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

import scala.concurrent.duration._

sealed trait StateStoreConfig

object StateStoreConfig extends Logging {
  import io.circe.generic.semiauto._

  case class RedisStateConfig(
      host: Hostname,
      port: Port,
      db: DBConfig = DBConfig(),
      cache: CacheConfig = CacheConfig(),
      pipeline: PipelineConfig = PipelineConfig(),
      format: StoreFormat = BinaryStoreFormat,
      auth: Option[RedisCredentials] = None
  ) extends StateStoreConfig

  object RedisStateConfig {
    import ai.metarank.util.DurationJson._
    case class DBConfig(state: Int = 0, values: Int = 1, rankings: Int = 2, models: Int = 3)
    implicit val dbDecoder: Decoder[DBConfig] = deriveDecoder[DBConfig]

    case class PipelineConfig(maxSize: Int = 128, flushPeriod: FiniteDuration = 1.second)
    implicit val pipelineConfigDecoder: Decoder[PipelineConfig] = Decoder.instance(c =>
      for {
        maxSize     <- c.downField("maxSize").as[Option[Int]]
        flushPeriod <- c.downField("flushPeriod").as[Option[FiniteDuration]]
      } yield {
        PipelineConfig(
          maxSize = maxSize.getOrElse(PipelineConfig().maxSize),
          flushPeriod = flushPeriod.getOrElse(PipelineConfig().flushPeriod)
        )
      }
    )

    case class CacheConfig(maxSize: Int = 4096, ttl: FiniteDuration = 1.hour)

    implicit val cacheConfigDecoder: Decoder[CacheConfig] = Decoder.instance(c =>
      for {
        maxSize <- c.downField("maxSize").as[Option[Int]]
        ttl     <- c.downField("ttl").as[Option[FiniteDuration]]
      } yield {
        CacheConfig(
          maxSize = maxSize.getOrElse(CacheConfig().maxSize),
          ttl = ttl.getOrElse(CacheConfig().ttl)
        )
      }
    )
  }

  case class RedisCredentials(user: Option[String] = None, password: String)
  implicit val redisCredentialsDecoder: Decoder[RedisCredentials] = deriveDecoder[RedisCredentials]

  case class MemoryStateConfig() extends StateStoreConfig

  implicit val redisConfigDecoder: Decoder[RedisStateConfig] = Decoder.instance(c =>
    for {
      host   <- c.downField("host").as[Hostname]
      port   <- c.downField("port").as[Port]
      db     <- c.downField("db").as[Option[DBConfig]]
      cache  <- c.downField("cache").as[Option[CacheConfig]]
      pipe   <- c.downField("pipeline").as[Option[PipelineConfig]]
      format <- c.downField("format").as[Option[StoreFormat]]
      auth   <- c.downField("auth").as[Option[RedisCredentials]]
    } yield {
      RedisStateConfig(
        host = host,
        port = port,
        db = db.getOrElse(DBConfig()),
        cache = cache.getOrElse(CacheConfig()),
        pipeline = pipe.getOrElse(PipelineConfig()),
        format = format.getOrElse(BinaryStoreFormat),
        auth = auth
      )
    }
  )

  implicit val memConfigDecoder: Decoder[MemoryStateConfig] = Decoder.instance(c => Right(MemoryStateConfig()))

  implicit val stateStoreConfigDecoder: Decoder[StateStoreConfig] = Decoder.instance(c =>
    c.downField("type").as[String].flatMap {
      case "redis"  => redisConfigDecoder(c)
      case "memory" => memConfigDecoder(c)
      case other    => Left(DecodingFailure(s"state store type '$other' is not supported", c.history))
    }
  )
}
