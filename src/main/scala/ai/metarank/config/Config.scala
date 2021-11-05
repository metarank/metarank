package ai.metarank.config

import ai.metarank.config.Config.{ApiConfig, SchemaConfig}
import ai.metarank.model.{FeatureSchema, FieldSchema}
import io.circe.Decoder
import io.circe.generic.semiauto._

case class Config(api: ApiConfig, schema: SchemaConfig, feature: List[FeatureSchema], ingest: IngestConfig)

object Config {
  case class ApiConfig(port: Int)
  case class SchemaConfig(metadata: List[FieldSchema], impression: List[FieldSchema], interaction: List[FieldSchema])

  implicit val schemaConfigDecoder: Decoder[SchemaConfig] = deriveDecoder
  implicit val apiConfigDecoder: Decoder[ApiConfig]       = deriveDecoder
  implicit val configDecoder: Decoder[Config]             = deriveDecoder
}
