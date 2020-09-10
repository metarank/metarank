package me.dfdx.metarank.config

import io.circe._
import io.circe.generic.semiauto._
import io.circe.yaml.parser._
import me.dfdx.metarank.config.Config.{CoreConfig, KeyspaceConfig}

case class Config(core: CoreConfig, keyspace: KeyspaceConfig) {
  def withCommandLineOverrides(cmd: CommandLineConfig): Config = {
    val iface = cmd.hostname.getOrElse(core.listen.hostname)
    val port  = cmd.port.getOrElse(core.listen.port)
    copy(core =
      core.copy(listen =
        core.listen.copy(
          hostname = iface,
          port = port
        )
      )
    )
  }
}

object Config {
  case class KeyspaceConfig(name: String, feedback: FeedbackConfig, schema: SchemaConfig)
  case class CoreConfig(listen: ListenConfig)
  case class ListenConfig(hostname: String, port: Int)

  case class InteractionType(value: String) extends AnyVal
  case class FeedbackConfig(types: List[FeedbackTypeConfig])
  case class FeedbackTypeConfig(name: InteractionType, weight: Int, features: List[FeatureConfig])
  case class FeatureConfig(name: String, windows: List[Int])

  case class SchemaConfig(windows: List[SchemaWindowConfig])
  case class SchemaWindowConfig(start: Int, size: Int)

  case class FieldConfig(name: String, format: FieldFormatConfig)
  case class FieldFormatConfig(`type`: String, repeated: Boolean, required: Boolean)

  implicit val interactionTypeEncoder = Encoder.instance[InteractionType](tpe => Encoder.encodeString(tpe.value))

  implicit val fieldFormatConfigDecoder  = deriveDecoder[FieldFormatConfig]
  implicit val fieldConfigDecoder        = deriveDecoder[FieldConfig]
  implicit val schemaWindowConfigDecoder = deriveDecoder[SchemaWindowConfig]
  implicit val schemaConfigDecoder       = deriveDecoder[SchemaConfig]
  implicit val featureConfigDecoder = deriveDecoder[FeatureConfig]
    .ensure(_.windows.nonEmpty, "windows cannot be empty")
    .ensure(!_.windows.contains(0), "zero length windows are impossible")

  implicit val interactionTypeDecoder = Decoder.decodeString
    .map(InteractionType.apply)
    .ensure(_.value.nonEmpty, "interaction type cannot be empty")
  implicit val feedbackTypeConfigDecoder = deriveDecoder[FeedbackTypeConfig]
  implicit val feedbackConfigDecoder     = deriveDecoder[FeedbackConfig]
  implicit val listenConfigDecoder       = deriveDecoder[ListenConfig]
  implicit val coreConfigDecoder         = deriveDecoder[CoreConfig]
  implicit val keyspaceConfigDecoder     = deriveDecoder[KeyspaceConfig]
  implicit val configDecoder             = deriveDecoder[Config]

  def load(configString: String): Either[ConfigLoadingError, Config] = {
    parse(configString) match {
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
