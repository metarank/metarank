package ai.metarank.config

import ai.metarank.config.Config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.Config.{BootstrapConfig, InferenceConfig, ModelConfig}
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}

import scala.util.{Failure, Success}

case class Config(
    features: NonEmptyList[FeatureSchema],
    models: NonEmptyMap[String, ModelConfig],
    bootstrap: BootstrapConfig,
    inference: InferenceConfig
)

object Config extends Logging {

  case class BootstrapConfig(eventPath: MPath, workdir: MPath, parallelism: Int = 1)
  object BootstrapConfig {
    import io.circe.generic.extras.semiauto._
    implicit val config: io.circe.generic.extras.Configuration = Configuration.default.withDefaults
    implicit val bootstrapDecoder: Decoder[BootstrapConfig]    = deriveConfiguredDecoder[BootstrapConfig]

  }
  case class InferenceConfig(
      port: Int = 8080,
      host: String = "0.0.0.0",
      state: StateStoreConfig,
      parallelism: Int = 1
  )
  object InferenceConfig {
    import io.circe.generic.extras.semiauto._
    implicit val config                                     = Configuration.default.withDefaults
    implicit val inferenceDecoder: Decoder[InferenceConfig] = deriveConfiguredDecoder[InferenceConfig]
  }

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
      case class LightGBMBackend(iterations: Int = 100) extends ModelBackend
      case class XGBoostBackend(iterations: Int = 100)  extends ModelBackend
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
      case (name, LambdaMARTConfig(_, _, features, _)) =>
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
