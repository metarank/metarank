package ai.metarank.model

import cats.data.NonEmptyList
import io.circe.{Codec, Decoder}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

import java.util.IllegalFormatException
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Try}

sealed trait FeatureSchema {
  def name: String
  def refresh: Option[FiniteDuration]
  def ttl: Option[FiniteDuration]
  def scope: FeatureScope
}

object FeatureSchema {
  case class NumberFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  case class BooleanFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  case class StringFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      values: NonEmptyList[String],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  case class WordCountSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  val durationFormat = "([0-9]+)([smhd]{1})".r
  implicit val durationDecoder: Decoder[FiniteDuration] = Decoder.decodeString.emapTry {
    case durationFormat(num, suffix) => Try(FiniteDuration(num.toLong, suffix))
    case d                           => Failure(new IllegalArgumentException(s"duration is in wrong format: $d"))
  }

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withKebabCaseMemberNames
    .copy(transformConstructorNames = {
      case "NumberFeatureSchema"  => "number"
      case "BooleanFeatureSchema" => "boolean"
      case "StringFeatureSchema"  => "string"
      case "WordCountSchema"      => "word_count"
    })

  implicit val featureSchemaDecoder: Decoder[FeatureSchema] = deriveConfiguredDecoder[FeatureSchema]
}
