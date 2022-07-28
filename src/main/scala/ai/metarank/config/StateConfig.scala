package ai.metarank.config

import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto._
import io.circe.literal._

sealed trait StateConfig {
  def host: Hostname
  def port: Port
}

object StateConfig {
  case class RedisStateConfig(host: Hostname, port: Port) extends StateConfig
  case class MemoryStateConfig(port: Port) extends StateConfig {
    val host = Hostname("localhost")
  }

  implicit val redisStateDecoder: Decoder[RedisStateConfig]   = deriveDecoder[RedisStateConfig]
  implicit val memoryStateDecoder: Decoder[MemoryStateConfig] = deriveDecoder[MemoryStateConfig]

  implicit val stateConfigDecoder: Decoder[StateConfig] = Decoder.instance(c =>
    for {
      state <- c.downField("type").as[String] match {
        case Left(error)     => Left(error)
        case Right("redis")  => redisStateDecoder.tryDecode(c)
        case Right("memory") => memoryStateDecoder.tryDecode(c)
        case Right(other)    => Left(DecodingFailure(s"state type $other is not supported", c.history))
      }
    } yield state
  )
}
