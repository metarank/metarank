package ai.metarank.config

import ai.metarank.config.StateStoreConfig.RedisStateConfig.DBConfig
import ai.metarank.util.Logging
import io.circe.{Decoder, DecodingFailure}

sealed trait StateStoreConfig

object StateStoreConfig extends Logging {
  import io.circe.generic.semiauto._

  case class RedisStateConfig(host: Hostname, port: Port, db: DBConfig = DBConfig()) extends StateStoreConfig

  object RedisStateConfig {
    case class DBConfig(state: Int = 0, values: Int = 1, rankings: Int = 2, hist: Int = 3, models: Int = 4)
    implicit val dbDecoder: Decoder[DBConfig] = deriveDecoder[DBConfig]
  }

  case class MemoryStateConfig() extends StateStoreConfig

  implicit val redisConfigDecoder: Decoder[RedisStateConfig] = Decoder.instance(c =>
    for {
      host <- c.downField("host").as[Hostname]
      port <- c.downField("port").as[Port]
      db   <- c.downField("db").as[Option[DBConfig]]
    } yield {
      RedisStateConfig(host, port, db.getOrElse(DBConfig()))
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
