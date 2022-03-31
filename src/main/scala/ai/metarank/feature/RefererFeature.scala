package ai.metarank.feature

import ai.metarank.feature.ua.{BotField, BrowserField, OSField, PlatformField}
import ai.metarank.model.{FeatureSchema, FeatureScope, FieldName}
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder
import ua_parser.Client

import scala.concurrent.duration.FiniteDuration

case class RefererFeature()

object RefererFeature {
  import ai.metarank.util.DurationJson._

  case class RefererSchema(
      name: String,
      source: FieldName,
      field: RefererField,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  trait RefererField {
    lazy val dim: Int = possibleValues.size
    def possibleValues: List[String]
    def value(client: Client): Option[String]
  }
  implicit val uafieldDecoder: Decoder[UAField] = Decoder.instance(c =>
    c.as[String] match {
      case Left(value)       => Left(value)
      case Right("platform") => Right(PlatformField)
      case Right("os")       => Right(OSField)
      case Right("browser")  => Right(BrowserField)
      case Right("bot")      => Right(BotField)
      case Right(other)      => Left(DecodingFailure(s"UA field type $other is not yet supported", c.history))
    }
  )

  implicit val uaDecoder: Decoder[UserAgentSchema] = deriveDecoder

}
