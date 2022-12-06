package ai.metarank.config

import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{Clickthrough, FieldName}
import cats.data.NonEmptyList
import io.circe.{ACursor, Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto._

import scala.util.Random

sealed trait Selector {
  def accept(event: Clickthrough): Boolean
}

object Selector {
  case class NotSelector(not: Selector) extends Selector {
    override def accept(event: Clickthrough): Boolean = !not.accept(event)
  }
  case class OrSelector(or: List[Selector]) extends Selector {
    override def accept(event: Clickthrough): Boolean = or.exists(_.accept(event))
  }
  case class AndSelector(and: List[Selector]) extends Selector {
    override def accept(event: Clickthrough): Boolean = and.forall(_.accept(event))
  }
  case class SampleSelector(ratio: Double) extends Selector {
    override def accept(event: Clickthrough): Boolean = Random.nextDouble() < ratio
  }
  case class FieldSelector(rankingField: String, value: String) extends Selector {
    override def accept(event: Clickthrough): Boolean = event.rankingFields.exists {
      case StringField(name, actual) if name == rankingField     => value == actual
      case StringListField(name, values) if name == rankingField => values.contains(value)
      case _                                                     => false
    }
  }

  case class AcceptSelector(accept: Boolean = true) extends Selector {
    override def accept(event: Clickthrough): Boolean = accept
  }

  implicit val fieldSelectorCodec: Codec[FieldSelector] = deriveCodec

  implicit val sampleSelectorEncoder: Encoder[SampleSelector] = deriveEncoder
  implicit val sampleSelectorDecoder: Decoder[SampleSelector] = deriveDecoder[SampleSelector].ensure(
    s => (s.ratio >= 0.0) && (s.ratio <= 1.0),
    "ratio should be withing 0.0..1.0 range"
  )
  implicit val sampleSelectorCodec: Codec[SampleSelector] = Codec.from(sampleSelectorDecoder, sampleSelectorEncoder)

  implicit val andSelectorCodec: Codec[AndSelector]       = deriveCodec
  implicit val orSelectorCodec: Codec[OrSelector]         = deriveCodec
  implicit val notSelectorCodec: Codec[NotSelector]       = deriveCodec
  implicit val acceptSelectorCodec: Codec[AcceptSelector] = deriveCodec

  implicit val selectorDecoder: Decoder[Selector] = Decoder.instance(c =>
    decodeChain[Selector](
      c,
      NonEmptyList.of(
        fieldSelectorCodec,
        sampleSelectorCodec,
        andSelectorCodec,
        orSelectorCodec,
        notSelectorCodec,
        acceptSelectorCodec
      )
    )
  )

  def decodeChain[A](c: ACursor, decoders: NonEmptyList[Decoder[_ <: A]]): Either[DecodingFailure, A] = {
    NonEmptyList.fromList(decoders.tail) match {
      case None => decoders.head.tryDecode(c)
      case Some(tail) =>
        decoders.head.tryDecode(c) match {
          case Left(_)      => decodeChain(c, tail)
          case Right(value) => Right(value)
        }
    }
  }

  implicit val selectorEncoder: Encoder[Selector] = Encoder.instance {
    case f: FieldSelector  => fieldSelectorCodec(f)
    case s: SampleSelector => sampleSelectorEncoder(s)
    case a: AndSelector    => andSelectorCodec(a)
    case o: OrSelector     => orSelectorCodec(o)
    case n: NotSelector    => notSelectorCodec(n)
    case a: AcceptSelector => acceptSelectorCodec(a)
  }
  implicit val selectorCodec: Codec[Selector] = Codec.from(selectorDecoder, selectorEncoder)
}
