package ai.metarank.config

import ai.metarank.config.Config.ModelConfig.LambdaMART
import ai.metarank.config.Config.{InteractionConfig, ModelConfig}
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
    interactions: NonEmptyList[InteractionConfig],
    models: NonEmptyMap[String, ModelConfig]
)

object Config extends Logging {
  case class InteractionConfig(name: String, weight: Double)

  sealed trait ModelConfig

  object ModelConfig {
    import io.circe.generic.extras.semiauto._
    case class LambdaMART(backend: ModelBackend, features: NonEmptyList[String]) extends ModelConfig
    case class Shuffle()                                                         extends ModelConfig
    case class Noop()                                                            extends ModelConfig

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
        case "LambdaMART" => "lambdamart"
        case "Shuffle"    => "shuffle"
        case "Noop"       => "noop"
      })
  implicit val modelConfigDecoder: Decoder[ModelConfig] = io.circe.generic.extras.semiauto.deriveConfiguredDecoder

  implicit val intDecoder: Decoder[InteractionConfig] = deriveDecoder
  implicit val configDecoder: Decoder[Config]         = deriveDecoder[Config].ensure(validateConfig)

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
    val ints     = nonUniqueNames[InteractionConfig](conf.interactions, _.name).map(_.toString("interaction"))
    val modelFeatures = conf.models.toNel.toList.flatMap {
      case (name, LambdaMART(_, features)) =>
        val undefined = features.filterNot(feature => conf.features.exists(_.name == feature))
        undefined.map(feature => s"unresolved feature '$feature' in model '$name'")
      case _ => Nil
    }
    features ++ ints ++ modelFeatures
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
