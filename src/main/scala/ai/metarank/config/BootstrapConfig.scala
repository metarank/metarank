package ai.metarank.config

import ai.metarank.config.BootstrapConfig.SyntheticImpressionConfig
import io.circe.Decoder
import io.circe.generic.extras.Configuration

case class BootstrapConfig(
                            source: InputConfig,
                            workdir: MPath,
                            parallelism: Option[Int] = None,
                            syntheticImpression: SyntheticImpressionConfig = SyntheticImpressionConfig()
)
object BootstrapConfig {
  import io.circe.generic.extras.semiauto._

  case class SyntheticImpressionConfig(enabled: Boolean = true, eventName: String = "impression")

  implicit val config: io.circe.generic.extras.Configuration = Configuration.default.withDefaults
  implicit val impressionDecoder: Decoder[SyntheticImpressionConfig] =
    deriveConfiguredDecoder[SyntheticImpressionConfig]
  implicit val bootstrapDecoder: Decoder[BootstrapConfig] = deriveConfiguredDecoder[BootstrapConfig]
}
