package ai.metarank.config

import ai.metarank.config.Config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.Config.ModelConfig
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}

import scala.util.{Failure, Success}

case class Config(
    features: NonEmptyList[FeatureSchema],
    models: NonEmptyMap[String, ModelConfig]
)

object Config extends Logging {
  sealed trait ModelConfig

  object ModelConfig {
    import io.circe.generic.extras.semiauto._
    case class LambdaMARTConfig(
        backend: ModelBackend,
        features: NonEmptyList[String],
        weights: NonEmptyMap[String, Double]
    ) extends ModelConfig
    case class ShuffleConfig(maxPositionChange: Int) extends ModelConfig
    case class NoopConfig()                          extends ModelConfig

    sealed trait ModelBackend
    case object LightGBMBackend extends ModelBackend
    case object XGBoostBackend  extends ModelBackend

    implicit val modelBackendDecoder: Decoder[ModelBackend] = Decoder.decodeString.emapTry {
      case "lightgbm" => Success(LightGBMBackend)
      case "xgboost"  => Success(XGBoostBackend)
      case other      => Failure(new IllegalArgumentException(s"model backend $other is not supported"))
    }

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

  implicit val configDecoder: Decoder[Config] = deriveDecoder[Config].ensure(validateConfig)

  def load(path: File): IO[Config] = for {
    contents <- IO { path.contentAsString }
    config   <- load(contents)
    _        <- IO(logger.info(s"loaded config file from $path"))
  } yield {
    config
  }

  def load(contents: String): IO[Config] = {
    for {
      yaml    <- IO.fromEither(parseYaml(contents))
      decoded <- IO.fromEither(yaml.as[Config])
      _       <- IO(logger.info(s"features: ${decoded.features.map(_.name)}"))
    } yield {
      decoded
    }
  }

  def validateConfig(conf: Config): List[String] = {
    val features = nonUniqueNames[FeatureSchema](conf.features, _.name).map(_.toString("feature"))
    val modelFeatures = conf.models.toNel.toList.flatMap {
      case (name, LambdaMARTConfig(_, features, _)) =>
        val undefined = features.filterNot(feature => conf.features.exists(_.name == feature))
        undefined.map(feature => s"unresolved feature '$feature' in model '$name'")
      case _ => Nil
    }
    features ++ modelFeatures
  }

  case class NonUniqueName(name: String, count: Int) {
    def toString(prefix: String) = s"non-unique $prefix '$name' is defined more than once"
  }
  private def nonUniqueNames[T](list: NonEmptyList[T], name: T => String): List[NonUniqueName] = {
    list.map(name).groupBy(identity).filter(_._2.size > 1).toList.map { case (key, values) =>
      NonUniqueName(key, values.size)
    }
  }
}
