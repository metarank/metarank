package ai.metarank.model

import io.circe.{Codec, Decoder, DecodingFailure}

sealed trait FieldSchema {
  def name: String
  def required: Boolean
}

object FieldSchema {
  case class BooleanFieldSchema(name: String, required: Boolean = false)    extends FieldSchema
  case class NumberFieldSchema(name: String, required: Boolean = false)     extends FieldSchema
  case class StringFieldSchema(name: String, required: Boolean = false)     extends FieldSchema
  case class NumberListFieldSchema(name: String, required: Boolean = false) extends FieldSchema
  case class StringListFieldSchema(name: String, required: Boolean = false) extends FieldSchema
  case class IPAddressFieldSchema(name: String, required: Boolean = false)  extends FieldSchema
  case class UAFieldSchema(name: String, required: Boolean = false)         extends FieldSchema
  case class RefererFieldSchema(name: String, required: Boolean = false)    extends FieldSchema

  implicit val fieldSchemaDecoder: Decoder[FieldSchema] = Decoder.instance(c =>
    for {
      name     <- c.downField("name").as[String]
      required <- c.downField("required").as[Option[Boolean]].map(_.getOrElse(false))
      tpe      <- c.downField("type").as[String]
      field <- tpe match {
        case "boolean"      => Right(BooleanFieldSchema(name, required))
        case "number"       => Right(NumberFieldSchema(name, required))
        case "string"       => Right(StringFieldSchema(name, required))
        case "list<string>" => Right(StringListFieldSchema(name, required))
        case "list<number>" => Right(NumberListFieldSchema(name, required))
        case "ip"           => Right(IPAddressFieldSchema(name, required))
        case "ua"           => Right(UAFieldSchema(name, required))
        case "referer"      => Right(RefererFieldSchema(name, required))
        case other          => Left(DecodingFailure(s"field type $other is not supported", c.history))
      }
    } yield {
      field
    }
  )
}
