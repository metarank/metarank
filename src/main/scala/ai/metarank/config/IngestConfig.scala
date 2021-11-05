package ai.metarank.config

import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder

sealed trait IngestConfig

object IngestConfig {
  case class FileIngestConfig(path: String) extends IngestConfig
  case class APIIngestConfig(port: Int)     extends IngestConfig

  implicit val config = Configuration.default
    .withDiscriminator("type")
    .copy(transformConstructorNames = {
      case "FileIngestConfig" => "file"
      case "APIIngestConfig"  => "api"
    })

  implicit val ingestDecoder: Decoder[IngestConfig] = deriveConfiguredDecoder
}
