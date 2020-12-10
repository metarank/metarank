package me.dfdx.metarank.config

import cats.data.NonEmptyList
import io.circe._
import io.circe.generic.semiauto._
import io.circe.yaml.parser._
import me.dfdx.metarank.config.Config.FieldType.{BooleanType, NumericType, StringType}
import me.dfdx.metarank.config.Config.{CoreConfig, FeaturespaceConfig}
import me.dfdx.metarank.model.{Featurespace, Field, Language}

import scala.util.{Failure, Success}

case class Config(core: CoreConfig, featurespace: List[FeaturespaceConfig]) {
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
      id: Featurespace,
      language: Language,
      store: StoreConfig,
      features: List[FeatureConfig],
      aggregations: NonEmptyList[AggregationConfig],
      schema: SchemaConfig
  )
  case class CoreConfig(listen: ListenConfig)
  case class ListenConfig(hostname: String, port: Int)

  case class WindowConfig(from: Int, length: Int) {
    val to = from + length
  }
  case class SchemaConfig(fields: NonEmptyList[FieldConfig]) {
    private val fieldMap                      = fields.map(field => field.name -> field.format).toNem
    def fieldExists(name: String): Boolean    = fieldMap.contains(name)
    def fieldTypeValid(field: Field): Boolean = fieldMap.lookup(field.name).map(_.`type`).contains(field.tpe)
  }
  case class FieldConfig(name: String, format: FieldFormatConfig)
  case class FieldFormatConfig(`type`: FieldType, repeated: Boolean = false, required: Boolean = true)

  sealed trait FieldType {
    def name: String
  }
  object FieldType {
    case object StringType extends FieldType {
      override val name = "string"
    }
    case object NumericType extends FieldType {
      override val name = "numeric"
    }
    case object BooleanType extends FieldType {
      override val name = "boolean"
    }
  }

  implicit val fieldTypeCodec = Codec.from[FieldType](
    decodeA = Decoder.decodeString.emapTry {
      case StringType.name  => Success(StringType)
      case NumericType.name => Success(NumericType)
      case BooleanType.name => Success(BooleanType)
      case other            => Failure(UnsupportedTypeError(other))
    },
    encodeA = Encoder.encodeString.contramap[FieldType] {
      case FieldType.StringType  => StringType.name
      case FieldType.NumericType => NumericType.name
      case FieldType.BooleanType => BooleanType.name
    }
  )

  implicit val featurespaceNameConfig = Codec.from(
    decodeA =
      Decoder.decodeString.map(Featurespace.apply).ensure(_.name.nonEmpty, "featurespace id should not be empty"),
    encodeA = Encoder.encodeString.contramap[Featurespace](_.name)
  )
  implicit val fieldFormatCodec = deriveCodec[FieldFormatConfig]
  implicit val fieldCodec       = deriveCodec[FieldConfig]
  implicit val schemaCodec      = deriveCodec[SchemaConfig]

  implicit val windowConfigCodec = Codec.from(
    decodeA = deriveDecoder[WindowConfig]
      .ensure(_.from > 0, "window start must be above zero")
      .ensure(_.length > 0, "window length must be above zero"),
    encodeA = deriveEncoder[WindowConfig]
  )

  import FeatureConfig._
  implicit val languageCodec = Codec.from(
    decodeA = Decoder.decodeString.emapTry(code => Language.fromCode(code).toTry),
    encodeA = Encoder.encodeString.contramap[Language](_.code)
  )

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
  case class UnsupportedTypeError(tpe: String)                     extends ConfigLoadingError(s"$tpe is not supported")
}
