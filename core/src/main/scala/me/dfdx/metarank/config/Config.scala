package me.dfdx.metarank.config

import cats.data.NonEmptyList
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
  case class FeaturespaceConfig(
      name: String,
      features: List[FeatureConfig],
      aggregations: List[AggregationConfig]
  )
  case class CoreConfig(listen: ListenConfig, store: StoreConfig)
  case class ListenConfig(hostname: String, port: Int)

  case class WindowConfig(from: Int, length: Int) {
    val to = from + length
  }

  case class FieldConfig(name: String, format: FieldFormatConfig)
  case class FieldFormatConfig(`type`: String, repeated: Boolean, required: Boolean)

  implicit val fieldFormatCodec = deriveCodec[FieldFormatConfig]
  implicit val fieldCodec       = deriveCodec[FieldConfig]

  implicit val windowConfigCodec = Codec.from(
    decodeA = deriveDecoder[WindowConfig]
      .ensure(_.from > 0, "window start must be above zero")
      .ensure(_.length > 0, "window length must be above zero"),
    encodeA = deriveEncoder[WindowConfig]
  )

  import FeatureConfig._

  implicit val listenConfigCodec       = deriveCodec[ListenConfig]
  implicit val coreConfigCodec         = deriveCodec[CoreConfig]
  implicit val featurespaceConfigCodec = deriveCodec[FeaturespaceConfig]
  implicit val configCodec             = deriveCodec[Config]

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
