package ai.metarank.model

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.feature.WordCountFeature.WordCountSchema
import cats.data.NonEmptyList
import io.circe.{Codec, Decoder}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

import java.util.IllegalFormatException
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Try}

trait FeatureSchema {
  def name: String
  def refresh: Option[FiniteDuration]
  def ttl: Option[FiniteDuration]
  def scope: FeatureScope
}

object FeatureSchema {

  implicit val conf = Configuration.default
    .withDiscriminator("type")
    .withKebabCaseMemberNames
    .copy(transformConstructorNames = {
      case "NumberFeatureSchema"  => "number"
      case "BooleanFeatureSchema" => "boolean"
      case "StringFeatureSchema"  => "string"
      case "WordCountSchema"      => "word_count"
    })

  implicit val featureSchemaDecoder: Decoder[FeatureSchema] = Decoder.instance(c =>
    for {
      tpe <- c.downField("type").as[String]
      decoded <- tpe match {
        case "number"     => implicitly[Decoder[NumberFeatureSchema]].apply(c)
        case "boolean"    => implicitly[Decoder[BooleanFeatureSchema]].apply(c)
        case "string"     => implicitly[Decoder[StringFeatureSchema]].apply(c)
        case "word_count" => implicitly[Decoder[WordCountSchema]].apply(c)
      }
    } yield {
      decoded
    }
  )
}
