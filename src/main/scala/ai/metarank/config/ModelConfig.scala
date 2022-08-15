package ai.metarank.config

import ai.metarank.model.Key.FeatureName
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.Decoder
import io.circe.generic.extras.Configuration

import scala.concurrent.duration._
import scala.util.Random

sealed trait ModelConfig

object ModelConfig {
  import io.circe.generic.extras.semiauto._
  import ai.metarank.util.DurationJson._

  case class LambdaMARTConfig(
      backend: ModelBackend,
      features: NonEmptyList[FeatureName],
      weights: Map[String, Double]
  ) extends ModelConfig
  case class ShuffleConfig(maxPositionChange: Int) extends ModelConfig
  case class NoopConfig()                          extends ModelConfig

  sealed trait ModelBackend {
    def iterations: Int
    def learningRate: Double
    def ndcgCutoff: Int
    def maxDepth: Int
    def seed: Int
  }
  object ModelBackend {
    case class LightGBMBackend(
        iterations: Int = 100,
        learningRate: Double = 0.1,
        ndcgCutoff: Int = 10,
        maxDepth: Int = 8,
        seed: Int = Random.nextInt(Int.MaxValue),
        numLeaves: Int = 16
    ) extends ModelBackend
    case class XGBoostBackend(
        iterations: Int = 100,
        learningRate: Double = 0.1,
        ndcgCutoff: Int = 10,
        maxDepth: Int = 8,
        seed: Int = Random.nextInt(Int.MaxValue)
    ) extends ModelBackend

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
