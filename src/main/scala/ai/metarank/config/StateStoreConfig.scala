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
  def format: StoreCodec[FeatureValue]
}
object StateStoreConfig {
  import io.circe.generic.extras.semiauto._

  implicit val formatDecoder: Decoder[StoreCodec[FeatureValue]] = Decoder.decodeString.emapTry {
    case "json"     => Success(FeatureValueJsonCodec)
    case "protobuf" => Success(FeatureValueProtobufCodec)
    case other      => Failure(new Exception(s"codec $other is not supported"))
  }

  case class RedisConfig(host: String, port: Int = 6379, format: StoreCodec[FeatureValue] = FeatureValueProtobufCodec)
      extends StateStoreConfig

  case class MemConfig(format: StoreCodec[FeatureValue] = FeatureValueProtobufCodec, port: Int = 6379)
      extends StateStoreConfig {
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
