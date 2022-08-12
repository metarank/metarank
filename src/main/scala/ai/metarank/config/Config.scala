package ai.metarank.config

import ai.metarank.config.InputConfig.ApiInputConfig
import ai.metarank.config.StateStoreConfig.MemoryStateConfig
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.IO
import io.circe.Decoder
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    api: ApiConfig,
    state: StateStoreConfig,
    input: InputConfig,
    features: NonEmptyList[FeatureSchema],
    models: NonEmptyMap[String, ModelConfig]
)

object Config extends Logging {

  implicit val configDecoder: Decoder[Config] = Decoder.instance(c =>
    for {
      apiOption   <- c.downField("api").as[Option[ApiConfig]]
      stateOption <- c.downField("state").as[Option[StateStoreConfig]]
      inputOption <- c.downField("input").as[Option[InputConfig]]
      features    <- c.downField("features").as[NonEmptyList[FeatureSchema]]
      models      <- c.downField("models").as[NonEmptyMap[String, ModelConfig]]
    } yield {
      val api   = get(apiOption, ApiConfig(Hostname("localhost"), Port(8080)), "api")
      val state = get(stateOption, MemoryStateConfig(), "state")
      val input = get(inputOption, ApiInputConfig(), "input")
      Config(api, state, input, features, models)
    }
  )

  def get[T](opt: Option[T], default: T, name: String) = opt match {
    case Some(value) => value
    case None =>
      logger.info(s"$name conf block is not defined: using default $default")
      default
  }

  def load(contents: String): IO[Config] = {
    for {
      yaml    <- IO.fromEither(parseYaml(contents))
      decoded <- IO.fromEither(yaml.as[Config])
    } yield {
      decoded
    }
  }
}
