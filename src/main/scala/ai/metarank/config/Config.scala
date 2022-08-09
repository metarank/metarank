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
    api: ApiConfig,
    state: StateStoreConfig,
    input: InputConfig,
    env: List[EnvConfig]
)

object Config extends Logging {

  implicit val configDecoder: Decoder[Config] = deriveDecoder[Config]

  def load(contents: String): IO[Config] = {
    for {
      yaml    <- IO.fromEither(parseYaml(contents))
      decoded <- IO.fromEither(yaml.as[Config])
    } yield {
      decoded
    }
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
