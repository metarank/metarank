package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto._

import scala.annotation.tailrec

sealed trait Field {
  def name: String
}

object Field {
  case class StringField(name: String, value: String)                    extends Field
  case class StringListField(name: String, value: NonEmptyList[String])  extends Field
  case class NumericField(name: String, value: Double)                   extends Field
  case class NumericListField(name: String, value: NonEmptyList[Double]) extends Field
  case class BooleanField(name: String, value: Boolean)                  extends Field

  implicit val stringDecoder = deriveDecoder[StringField]
    .ensure(_.value.nonEmpty, "field cannot be empty")

  implicit val stringListDecoder = deriveDecoder[StringListField]
    .ensure(_.value.forall(_.nonEmpty), "field cannot contain empty strings")

  implicit val numericDecoder = deriveDecoder[NumericField]
    .ensure(n => !java.lang.Double.isNaN(n.value), "field cannot be NaN")
    .ensure(n => !java.lang.Double.isInfinite(n.value), "field must be finite")

  implicit val numericListDecoder = deriveDecoder[NumericListField]
    .ensure(_.value.forall(n => !java.lang.Double.isNaN(n)), "field cannot contain NaN")
    .ensure(_.value.forall(n => !java.lang.Double.isInfinite(n)), "field cannot contain Inf")

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
}
