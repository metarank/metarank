package ai.metarank.config

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}

import scala.util.{Failure, Success}

sealed trait StateStoreConfig {
  def port: Int
  def host: String
  def format: StoreCodec
}
object StateStoreConfig {
  import io.circe.generic.extras.semiauto._

  implicit val formatDecoder: Decoder[StoreCodec] = Decoder.decodeString.emapTry {
    case "json"     => Success(JsonCodec)
    case "protobuf" => Success(ProtobufCodec)
    case other      => Failure(new Exception(s"codec $other is not supported"))
  }

  case class RedisConfig(host: String, port: Int = 6379, format: StoreCodec = JsonCodec) extends StateStoreConfig

  case class MemConfig(format: StoreCodec = JsonCodec, port: Int = 6379) extends StateStoreConfig {
    val host = "localhost"
  }

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withDefaults
    .copy(transformConstructorNames = {
      case "RedisConfig" => "redis"
      case "MemConfig"   => "memory"
    })
  implicit val stateStoreDecoder: Decoder[StateStoreConfig] = deriveConfiguredDecoder
}
