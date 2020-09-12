package me.dfdx.metarank.config

import io.circe._
import io.circe.generic.semiauto._
import io.circe.yaml.parser._
import me.dfdx.metarank.config.Config.{CoreConfig, FeaturespaceConfig}

case class Config(core: CoreConfig, featurespace: FeaturespaceConfig) {
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
  case class FeaturespaceConfig(name: String, state: List[InteractionStateConfig])
  case class CoreConfig(listen: ListenConfig)
  case class ListenConfig(hostname: String, port: Int)

  case class InteractionType(value: String) extends AnyVal
  case class InteractionStateConfig(interaction: InteractionType, trackers: List[TrackerConfig])
  case class TrackerConfig(name: String, days: Int)
  case class WindowConfig(from: Int, length: Int) {
    val to = from + length
  }

  case class FieldConfig(name: String, format: FieldFormatConfig)
  case class FieldFormatConfig(`type`: String, repeated: Boolean, required: Boolean)

  implicit val interactionTypeEncoder = Encoder.instance[InteractionType](tpe => Encoder.encodeString(tpe.value))

  implicit val fieldFormatConfigDecoder = deriveDecoder[FieldFormatConfig]
  implicit val fieldConfigDecoder       = deriveDecoder[FieldConfig]
  implicit val windowConfigDecoder = deriveDecoder[WindowConfig]
    .ensure(w => (w.from >= 0) && (w.length >= 0), "from/length window fields should be positive")
  implicit val featureConfigDecoder = deriveDecoder[TrackerConfig]
    .ensure(_.days > 0, "days should be above zero")

  implicit val interactionTypeDecoder = Decoder.decodeString
    .map(InteractionType.apply)
    .ensure(_.value.nonEmpty, "interaction type cannot be empty")
  implicit val feedbackTypeConfigDecoder = deriveDecoder[InteractionStateConfig]
  implicit val listenConfigDecoder       = deriveDecoder[ListenConfig]
  implicit val coreConfigDecoder         = deriveDecoder[CoreConfig]
  implicit val featurespaceConfigDecoder = deriveDecoder[FeaturespaceConfig]
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

  abstract class ConfigLoadingError(msg: String)                   extends Exception(msg)
  case class YamlDecodingError(msg: String, underlying: Throwable) extends ConfigLoadingError(msg)
  case class ConfigSyntaxError(msg: String, chain: List[CursorOp]) extends ConfigLoadingError(msg)
}
