package ai.metarank.config

import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import better.files.File
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    features: NonEmptyList[FeatureSchema],
    models: NonEmptyMap[String, ModelConfig],
    api: ApiConfig,
    state: StateStoreConfig,
    input: InputConfig
)

object Config extends Logging {

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
