package ai.metarank.config

import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.Decoder
import io.circe.generic.extras.Configuration

import scala.util.Random

sealed trait ModelConfig

object ModelConfig {
  import io.circe.generic.extras.semiauto._
  case class LambdaMARTConfig(
      path: MPath,
      backend: ModelBackend,
      features: NonEmptyList[String],
      weights: NonEmptyMap[String, Double]
  ) extends ModelConfig
  case class ShuffleConfig(maxPositionChange: Int) extends ModelConfig
  case class NoopConfig()                          extends ModelConfig

  sealed trait ModelBackend {
    def iterations: Int
  }
  object ModelBackend {
    case class LightGBMBackend(iterations: Int = 100, seed: Int = Random.nextInt(Int.MaxValue)) extends ModelBackend
    case class XGBoostBackend(iterations: Int = 100, seed: Int = Random.nextInt(Int.MaxValue))  extends ModelBackend
    implicit val conf =
      Configuration.default
        .withDiscriminator("type")
        .withDefaults
        .copy(transformConstructorNames = {
          case "LightGBMBackend" => "lightgbm"
          case "XGBoostBackend"  => "xgboost"
        })

    implicit val modelBackendDecoder: Decoder[ModelBackend] = deriveConfiguredDecoder
  }
  implicit val conf =
    Configuration.default
      .withDiscriminator("type")
      .copy(transformConstructorNames = {
        case "LambdaMARTConfig" => "lambdamart"
        case "ShuffleConfig"    => "shuffle"
        case "NoopConfig"       => "noop"
      })
  implicit val modelConfigDecoder: Decoder[ModelConfig] = io.circe.generic.extras.semiauto.deriveConfiguredDecoder
}
