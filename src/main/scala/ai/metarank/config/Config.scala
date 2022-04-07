package ai.metarank.config

import ai.metarank.config.Config.InteractionConfig
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe.yaml.parser.{parse => parseYaml}

case class Config(
    features: List[FeatureSchema],
    interactions: List[InteractionConfig]
)

object Config extends Logging {
  case class InteractionConfig(name: String, weight: Double)
  implicit val intDecoder: Decoder[InteractionConfig] = deriveDecoder
  implicit val configDecoder: Decoder[Config]         = deriveDecoder

  def load(path: File): IO[Config] = for {
    contents <- IO { path.contentAsString }
    config   <- load(contents)
    _        <- IO(logger.info(s"loaded config file from $path"))
  } yield {
    config
  }

  def load(contents: String): IO[Config] = {
    for {
      yaml    <- IO.fromEither(parseYaml(contents))
      decoded <- IO.fromEither(yaml.as[Config])
      _       <- IO(logger.info(s"features: ${decoded.features.map(_.name)}"))
    } yield {
      decoded
    }
  }

  def validateConfig(conf: Config): IO[Unit] = {
    val dupes = conf.features.map(_.name).groupBy(identity).filter(_._2.size != 1).keys.toList
    if (conf.features.isEmpty) {
      IO.raiseError(new IllegalArgumentException("there should be at least one defined feature"))
    } else if (dupes.nonEmpty) {
      IO.raiseError(new IllegalArgumentException(s"each feature should have unique name, but $dupes are not"))
    } else {
      IO.unit
    }
  }
}
