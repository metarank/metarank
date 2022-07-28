package ai.metarank.config

import ai.metarank.config.StateStoreConfig.DBConfig
import ai.metarank.util.Logging
import io.circe.{Decoder, DecodingFailure, HCursor}

import scala.util.Random

sealed trait StateStoreConfig {
  def port: Port
  def host: Hostname
  def db: DBConfig
}
object StateStoreConfig extends Logging {
  import io.circe.generic.semiauto._

  case class DBConfig(state: Int = 0, fields: Int = 1, clickthroughs: Int = 2, features: Int = 3)
  implicit val dbconfigDecoder: Decoder[DBConfig] = deriveDecoder[DBConfig]

  case class RedisStateConfig(host: Hostname, port: Port, db: DBConfig = DBConfig()) extends StateStoreConfig

  case class MemoryStateConfig(db: DBConfig = DBConfig()) extends StateStoreConfig {
    val host = Hostname("localhost")
    val port = Port(46379)
  }

  implicit val redisConfigDecoder: Decoder[RedisStateConfig] = Decoder.instance(c =>
    for {
      host <- c.downField("host").as[Hostname]
      port <- c.downField("port").as[Port]
      db   <- decodeDb(c)
    } yield { RedisStateConfig(host, port, db) }
  )

  implicit val memConfigDecoder: Decoder[MemoryStateConfig] = Decoder.instance(c =>
    for {
      db <- decodeDb(c)
    } yield {
      MemoryStateConfig(db)
    }
  )

  def decodeDb(c: HCursor): Either[DecodingFailure, DBConfig] = c.downField("db").as[Option[DBConfig]].map {
    case Some(value) => value
    case None =>
      val default = DBConfig()
      logger.info(s"using default redis db layout: $default")
      default
  }

  implicit val stateStoreConfigDecoder: Decoder[StateStoreConfig] = Decoder.instance(c =>
    c.downField("type").as[String].flatMap {
      case "redis"  => redisConfigDecoder(c)
      case "memory" => memConfigDecoder(c)
      case other    => Left(DecodingFailure(s"state store type '$other' is not supported", c.history))
    }
  )

}
