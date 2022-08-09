package ai.metarank.config

import ai.metarank.model.{Env, FeatureSchema}
import cats.data.{NonEmptyList, NonEmptyMap}
import io.circe.Decoder
import io.circe.generic.semiauto._

case class EnvConfig(name: Env, features: NonEmptyList[FeatureSchema], models: NonEmptyMap[String, ModelConfig])

object EnvConfig {
  implicit val envConfigDecoder: Decoder[EnvConfig] = deriveDecoder[EnvConfig]
}
