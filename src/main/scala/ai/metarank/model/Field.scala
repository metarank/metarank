package ai.metarank.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*

import java.util

sealed trait Field {
  def name: String
}

object Field {
  case class StringField(name: String, value: String)           extends Field
  case class BooleanField(name: String, value: Boolean)         extends Field
  case class NumberField(name: String, value: Double)           extends Field
  case class StringListField(name: String, value: List[String]) extends Field
  case class NumberListField(name: String, value: Array[Double]) extends Field {
    override def equals(obj: Any): Boolean = obj match {
      case NumberListField(xname, xvalues) => (name == xname) && (util.Arrays.equals(value, xvalues))
      case _                               => false
    }
  }

  object NumberListField {}

  def toString(fields: List[Field]) = fields
    .map {
      case Field.StringField(name, value)     => s"$name=$value"
      case Field.BooleanField(name, value)    => s"$name=$value"
      case Field.NumberField(name, value)     => s"$name=$value"
      case Field.StringListField(name, value) => s"$name=${value.mkString(",")}"
      case Field.NumberListField(name, value) => s"$name=${value.mkString(",")}"
    }
    .mkString("[", ", ", "]")

  given fieldDecoder: Decoder[Field] = Decoder.instance(c =>
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
            Right(NumberListField(name, values.flatMap(_.asNumber.map(_.toDouble)).toArray))
          case other =>
            Left(DecodingFailure(s"cannot decode field $name: got list of $other", c.history))
        },
        jsonObject = obj => Left(DecodingFailure(s"cannot decode field $name: got object $obj", c.history))
      )
    } yield {
      field
    }
  )

  given stringEncoder: Encoder[StringField]         = deriveEncoder
  given boolEncoder: Encoder[BooleanField]          = deriveEncoder
  given numEncoder: Encoder[NumberField]            = deriveEncoder
  given stringListEncoder: Encoder[StringListField] = deriveEncoder
  given numListEncoder: Encoder[NumberListField]    = deriveEncoder

  given fieldEncoder: Encoder[Field] = Encoder.instance {
    case f: StringField     => stringEncoder.apply(f)
    case f: BooleanField    => boolEncoder.apply(f)
    case f: NumberField     => numEncoder.apply(f)
    case f: StringListField => stringListEncoder.apply(f)
    case f: NumberListField => numListEncoder.apply(f)
  }

  given fieldCodec: Codec[Field] = Codec.from(fieldDecoder, fieldEncoder)
}
