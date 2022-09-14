package ai.metarank.main.command.autofeature

import ai.metarank.config.StateStoreConfig.MemoryStateConfig
import ai.metarank.config._
import ai.metarank.model.FeatureSchema
import cats.data.NonEmptyList
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ConfigMirror(
    features: NonEmptyList[FeatureSchema],
    models: Map[String, ModelConfig]
)
object ConfigMirror {
  implicit val configMirrorEncoder: Encoder[ConfigMirror] = deriveEncoder[ConfigMirror]
}
