package ai.metarank.config

import io.circe.Decoder
import io.circe.generic.extras.Configuration

case class InferenceConfig(
                            port: Int = 8080,
                            host: String = "0.0.0.0",
                            state: StateStoreConfig,
                            source: InputConfig,
                            parallelism: Option[Int] = None
)
object InferenceConfig {
  import io.circe.generic.extras.semiauto._
  implicit val config                                     = Configuration.default.withDefaults
  implicit val inferenceDecoder: Decoder[InferenceConfig] = deriveConfiguredDecoder[InferenceConfig]
}
