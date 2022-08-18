package ai.metarank.model

import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto._

sealed trait Field {
  def name: String
}

object Field {
  case class StringField(name: String, value: String)           extends Field
  case class BooleanField(name: String, value: Boolean)         extends Field
  case class NumberField(name: String, value: Double)           extends Field
  case class StringListField(name: String, value: List[String]) extends Field
  case class NumberListField(name: String, value: List[Double]) extends Field

  def toString(fields: List[Field]) = fields
    .map {
      case Field.StringField(name, value)     => s"$name=$value"
      case Field.BooleanField(name, value)    => s"$name=$value"
      case Field.NumberField(name, value)     => s"$name=$value"
      case Field.StringListField(name, value) => s"$name=${value.mkString(",")}"
      case Field.NumberListField(name, value) => s"$name=${value.mkString(",")}"
    }
    .mkString("[", ", ", "]")

  implicit val fieldDecoder: Decoder[Field] = Decoder.instance(c =>
    for {
      name <- c.downField("name").as[String]
      fieldJson <- c.downField("value").focus match {
        case Some(value) => Right(value)
        case None        => Left(DecodingFailure(s"field value not found", c.history))
      }
      field <- fieldJson.fold(
        jsonNull = Left(DecodingFailure(s"null value in field $name", c.history)),
        jsonBoolean = value => Right(BooleanField(name, value)),
        jsonNumber = value => Right(NumberField(name, value.toDouble)),
        jsonString = value => Right(StringField(name, value)),
        jsonArray = {
          case values if values.forall(_.isString) => Right(StringListField(name, values.flatMap(_.asString).toList))
          case values if values.forall(_.isNumber) =>
            Right(NumberListField(name, values.flatMap(_.asNumber.map(_.toDouble)).toList))
          case other =>
            Left(DecodingFailure(s"cannot decode field $name: got list of $other", c.history))
        },
        jsonObject = obj => Left(DecodingFailure(s"cannot decode field $name: got object $obj", c.history))
      )
    } yield {
      field
    }
  )

  implicit val stringEncoder: Encoder[StringField]         = deriveEncoder
  implicit val boolEncoder: Encoder[BooleanField]          = deriveEncoder
  implicit val numEncoder: Encoder[NumberField]            = deriveEncoder
  implicit val stringListEncoder: Encoder[StringListField] = deriveEncoder
  implicit val numListEncoder: Encoder[NumberListField]    = deriveEncoder

  implicit val fieldEncoder: Encoder[Field] = Encoder.instance {
    case f: StringField     => stringEncoder.apply(f)
    case f: BooleanField    => boolEncoder.apply(f)
    case f: NumberField     => numEncoder.apply(f)
    case f: StringListField => stringListEncoder.apply(f)
    case f: NumberListField => numListEncoder.apply(f)
  }
}
