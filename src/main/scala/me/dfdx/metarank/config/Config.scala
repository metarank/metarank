package me.dfdx.metarank.config

import io.circe.{Codec, CursorOp, Decoder}
import me.dfdx.metarank.config.Config.{CoreConfig, FeedbackConfig, SchemaConfig}
import io.circe.yaml.parser.{parse => parseYaml}
import io.circe.generic.semiauto._
import io.circe.parser._

case class Config(core: CoreConfig, feedback: FeedbackConfig, schema: SchemaConfig)

object Config {
  case class CoreConfig(listen: ListenConfig)
  case class ListenConfig(hostname: String, port: Int)

  case class FeedbackConfig(types: List[FeedbackTypeConfig])
  case class FeedbackTypeConfig(name: String, weight: Int)

  case class SchemaConfig(windows: List[SchemaWindowConfig])
  case class SchemaWindowConfig(start: Int, size: Int)

  case class FieldConfig(name: String, format: FieldFormatConfig)
  case class FieldFormatConfig(`type`: String, repeated: Boolean, required: Boolean)

  implicit val fieldFormatConfigDecoder  = deriveDecoder[FieldFormatConfig]
  implicit val fieldConfigDecoder        = deriveDecoder[FieldConfig]
  implicit val schemaWindowConfigDecoder = deriveDecoder[SchemaWindowConfig]
  implicit val schemaConfigDecoder       = deriveDecoder[SchemaConfig]
  implicit val feedbackTypeConfigDecoder = deriveDecoder[FeedbackTypeConfig]
  implicit val feedbackConfigDecoder     = deriveDecoder[FeedbackConfig]
  implicit val listenConfigDecoder       = deriveDecoder[ListenConfig]
  implicit val coreConfigDecoder         = deriveDecoder[CoreConfig]
  implicit val configDecoder             = deriveDecoder[Config]

  def load(configString: String): Either[ConfigLoadingError, Config] = {
    parseYaml(configString) match {
      case Left(err) => Left(YamlDecodingError(err.message, err.underlying))
      case Right(yaml) =>
        yaml.as[Config] match {
          case Left(err)     => Left(ConfigSyntaxError(err.message, err.history))
          case Right(config) => Right(config)
        }
    }
  }

  sealed trait ConfigLoadingError extends Throwable {
    def msg: String
  }
  case class YamlDecodingError(msg: String, underlying: Throwable) extends ConfigLoadingError
  case class ConfigSyntaxError(msg: String, chain: List[CursorOp]) extends ConfigLoadingError
}
