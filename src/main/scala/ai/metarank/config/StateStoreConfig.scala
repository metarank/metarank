package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig.{CacheConfig, DBConfig}
import ai.metarank.util.Logging
import io.circe.{Decoder, DecodingFailure}

import scala.concurrent.duration._

sealed trait StateStoreConfig

object StateStoreConfig extends Logging {
  import io.circe.generic.semiauto._

  case class RedisStateConfig(host: Hostname, port: Port, db: DBConfig = DBConfig(), cache: CacheConfig = CacheConfig())
      extends StateStoreConfig

  object RedisStateConfig {
    import ai.metarank.util.DurationJson._
    case class DBConfig(state: Int = 0, values: Int = 1, rankings: Int = 2, hist: Int = 3, models: Int = 4)
    implicit val dbDecoder: Decoder[DBConfig] = deriveDecoder[DBConfig]

    case class CacheConfig(pipelineSize: Int = 128, ttl: FiniteDuration = 1.hour, maxSize: Int = 32 * 1024)
    implicit val cacheConfigDecoder: Decoder[CacheConfig] = deriveDecoder[CacheConfig]
  }

  case class MemoryStateConfig() extends StateStoreConfig

  implicit val redisConfigDecoder: Decoder[RedisStateConfig] = Decoder.instance(c =>
    for {
      host  <- c.downField("host").as[Hostname]
      port  <- c.downField("port").as[Port]
      db    <- c.downField("db").as[Option[DBConfig]]
      cache <- c.downField("cache").as[Option[CacheConfig]]
    } yield {
      RedisStateConfig(host, port, db.getOrElse(DBConfig()), cache.getOrElse(CacheConfig()))
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
