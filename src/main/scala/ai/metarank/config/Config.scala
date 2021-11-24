package ai.metarank.config

import ai.metarank.model.{FeatureSchema, FieldSchema}
import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    feature: List[FeatureSchema]
)

object Config extends Logging {
  case class StoreConfig(host: String, port: Int)

  implicit val storeDecoder: Decoder[StoreConfig] = deriveDecoder
  implicit val configDecoder: Decoder[Config]     = deriveDecoder

  def load(path: String): IO[Config] = for {
    contents <- IO(File(path).contentAsString)
    yaml     <- IO.fromEither(parseYaml(contents))
    decoded  <- IO.fromEither(yaml.as[Config])
    _        <- IO(logger.info(s"loaded config file from $path"))
    _        <- IO(logger.info(s"features: ${decoded.feature.size}"))
  } yield {
    decoded
  }
}
