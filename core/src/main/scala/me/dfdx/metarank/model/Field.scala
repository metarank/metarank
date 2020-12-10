package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto._
import me.dfdx.metarank.config.Config.FieldType
import me.dfdx.metarank.config.Config.FieldType.{BooleanType, NumericType, StringType}
import me.dfdx.metarank.store.state.codec.Codec

import scala.annotation.tailrec

sealed trait Field {
  def name: String
  def tpe: FieldType
}

object Field {
  case class StringField(name: String, value: String) extends Field {
    override val tpe = StringType
  }
  case class StringListField(name: String, value: NonEmptyList[String]) extends Field {
    override val tpe = StringType
  }
  case class NumericField(name: String, value: Double) extends Field {
    override val tpe = NumericType
  }
  case class NumericListField(name: String, value: NonEmptyList[Double]) extends Field {
    override val tpe = NumericType
  }
  case class BooleanField(name: String, value: Boolean) extends Field {
    override val tpe = BooleanType
  }

  implicit val stringEncoder = deriveEncoder[StringField]
  implicit val stringDecoder = deriveDecoder[StringField]
    .ensure(_.value.nonEmpty, "field cannot be empty")

  implicit val stringListEncoder = deriveEncoder[StringListField]
  implicit val stringListDecoder = deriveDecoder[StringListField]
    .ensure(_.value.forall(_.nonEmpty), "field cannot contain empty strings")

  implicit val numericEncoder = deriveEncoder[NumericField]
  implicit val numericDecoder = deriveDecoder[NumericField]
    .ensure(n => !java.lang.Double.isNaN(n.value), "field cannot be NaN")
    .ensure(n => !java.lang.Double.isInfinite(n.value), "field must be finite")

  implicit val numericListEncoder = deriveEncoder[NumericListField]
  implicit val numericListDecoder = deriveDecoder[NumericListField]
    .ensure(_.value.forall(n => !java.lang.Double.isNaN(n)), "field cannot contain NaN")
    .ensure(_.value.forall(n => !java.lang.Double.isInfinite(n)), "field cannot contain Inf")

  implicit val booleanEncoder = deriveEncoder[BooleanField]
  implicit val booleanDecoder = deriveDecoder[BooleanField]

  private val decoderChain: List[Decoder[_ <: Field]] =
    List(stringDecoder, stringListDecoder, numericDecoder, numericListDecoder, booleanDecoder)

  @tailrec
  def decodeField(c: ACursor, chain: List[Decoder[_ <: Field]]): Decoder.Result[Field] =
    chain match {
      case Nil =>
        Left(DecodingFailure("cannot decode field", c.history))
      case next :: tail =>
        next.tryDecode(c) match {
          case Left(_)      => decodeField(c, tail)
          case Right(value) => Right(value)
        }

    }

  implicit val fieldDecoder: Decoder[Field] = Decoder.instance(c => decodeField(c, decoderChain))
  implicit val fieldEncoder = Encoder.instance[Field] {
    case f: StringField      => stringEncoder(f)
    case f: StringListField  => stringListEncoder(f)
    case f: NumericField     => numericEncoder(f)
    case f: NumericListField => numericListEncoder(f)
    case f: BooleanField     => booleanEncoder(f)
  }
}
