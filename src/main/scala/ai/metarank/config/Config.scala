package ai.metarank.config

import ai.metarank.config.Config.{ApiConfig, SchemaConfig, StoreConfig}
import ai.metarank.model.{FeatureSchema, FieldSchema}
import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    api: ApiConfig,
    schema: SchemaConfig,
    feature: List[FeatureSchema],
    ingest: IngestConfig,
    store: StoreConfig
)

object Config extends Logging {
  case class StoreConfig(host: String, port: Int)
  case class ApiConfig(port: Int)
  case class SchemaConfig(metadata: List[FieldSchema], impression: List[FieldSchema], interaction: List[FieldSchema]) {
    def fields = metadata ++ impression ++ interaction
  }

  implicit val storeDecoder: Decoder[StoreConfig]         = deriveDecoder
  implicit val schemaConfigDecoder: Decoder[SchemaConfig] = deriveDecoder
  implicit val apiConfigDecoder: Decoder[ApiConfig]       = deriveDecoder
  implicit val configDecoder: Decoder[Config]             = deriveDecoder

  def load(path: String): IO[Config] = for {
    contents <- IO(File(path).contentAsString)
    yaml     <- IO.fromEither(parseYaml(contents))
    decoded  <- IO.fromEither(yaml.as[Config])
    _        <- IO(logger.info(s"loaded config file from $path"))
    _        <- IO(logger.info(s"fields: ${decoded.schema.fields.size} features: ${decoded.feature.size}"))
  } yield {
    decoded
  }
}
