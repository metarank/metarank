package ai.metarank.config

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.findify.featury.model.FeatureValue
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{FeatureValueJsonCodec, FeatureValueProtobufCodec}

import scala.util.{Failure, Success}

sealed trait StateStoreConfig {
  def port: Int
  def host: String
}
object StateStoreConfig {
  import io.circe.generic.extras.semiauto._

  case class RedisConfig(host: String, port: Int = 6379) extends StateStoreConfig

  case class MemConfig(port: Int = 6379) extends StateStoreConfig {
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
