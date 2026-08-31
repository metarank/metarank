package ai.metarank.config

import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{Clickthrough, TrainValues}
import cats.data.NonEmptyList
import io.circe.{ACursor, Codec, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.*

import scala.util.Random

sealed trait Selector {
  def accept(tv: TrainValues): Boolean = tv match {
    case TrainValues.ClickthroughValues(ct, _) => accept(ct)
    case _: TrainValues.ItemValues             => true
    case _: TrainValues.UserValues             => true
  }
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
  case class InteractionPositionSelector(minInteractionPosition: Option[Int], maxInteractionPosition: Option[Int])
      extends Selector {
    override def accept(event: Clickthrough): Boolean = {
      val positionMap = event.items.zipWithIndex.toMap
      val positions = for {
        item     <- event.interactions
        position <- positionMap.get(item.item)
      } yield {
        position
      }
      val min = minInteractionPosition.getOrElse(Int.MaxValue)
      val max = maxInteractionPosition.getOrElse(Int.MaxValue)
      positions.forall(p => (p >= min) && (p <= max))
    }
  }

  case class RankingLengthSelector(minItems: Option[Int], maxItems: Option[Int]) extends Selector {
    override def accept(event: Clickthrough): Boolean = {
      val min  = minItems.getOrElse(Int.MinValue)
      val max  = maxItems.getOrElse(Int.MaxValue)
      val size = event.items.size
      (size >= min) && (size <= max)
    }
  }

  case class AcceptSelector(accept: Boolean = true) extends Selector {
    override def accept(event: Clickthrough): Boolean = accept
  }

  case class UserSelector(user: String) extends Selector {
    override def accept(event: Clickthrough): Boolean = event.user.exists(_.value == user)
  }

  // Selects clickthroughs landing in a fixed slot of a repeating period, to isolate traffic
  // from schedulers firing on a strict interval. Periods are aligned to the epoch, so a
  // 300s period starts on every wall-clock 5-minute mark in UTC.
  case class CadenceSelector(periodSeconds: Int, secondFrom: Int, secondTo: Int) extends Selector {
    override def accept(event: Clickthrough): Boolean = {
      val offset = Math.floorMod(Math.floorDiv(event.ts.ts, 1000L), periodSeconds.toLong)
      (offset >= secondFrom) && (offset <= secondTo)
    }
  }

  given fieldSelectorCodec: Codec[FieldSelector] = deriveCodec

  given userSelectorCodec: Codec[UserSelector] = deriveCodec

  given cadenceEncoder: Encoder[CadenceSelector] = deriveEncoder
  given cadenceDecoder: Decoder[CadenceSelector] = deriveDecoder[CadenceSelector].ensure(
    s => (s.periodSeconds > 0) && (s.secondFrom >= 0) && (s.secondTo >= s.secondFrom) && (s.secondTo < s.periodSeconds),
    "cadence selector needs 0 <= secondFrom <= secondTo < periodSeconds"
  )
  given cadenceCodec: Codec[CadenceSelector] = Codec.from(cadenceDecoder, cadenceEncoder)

  given rankingLengthEncoder: Encoder[RankingLengthSelector] = deriveEncoder
  given rankingLengthDecoder: Decoder[RankingLengthSelector] = deriveDecoder[RankingLengthSelector].ensure(
    s => s.maxItems.isDefined || s.minItems.isDefined,
    "min or max items should be defined"
  )
  given rankingLengthCodec: Codec[RankingLengthSelector] = Codec.from(rankingLengthDecoder, rankingLengthEncoder)

  given sampleSelectorEncoder: Encoder[SampleSelector] = deriveEncoder
  given sampleSelectorDecoder: Decoder[SampleSelector] = deriveDecoder[SampleSelector].ensure(
    s => (s.ratio >= 0.0) && (s.ratio <= 1.0),
    "ratio should be withing 0.0..1.0 range"
  )
  given sampleSelectorCodec: Codec[SampleSelector] = Codec.from(sampleSelectorDecoder, sampleSelectorEncoder)

  given maxPositionEncoder: Encoder[InteractionPositionSelector] = deriveEncoder
  given maxPositionDecoder: Decoder[InteractionPositionSelector] =
    deriveDecoder[InteractionPositionSelector].ensure(
      pred = s => s.maxInteractionPosition.isDefined || s.minInteractionPosition.isDefined,
      message = "max or min position should be defined"
    )
  given maxPositionCodec: Codec[InteractionPositionSelector] =
    Codec.from(maxPositionDecoder, maxPositionEncoder)

  given acceptSelectorCodec: Codec[AcceptSelector] = deriveCodec

  given selectorDecoder: Decoder[Selector] = Decoder.instance(c =>
    decodeChain[Selector](
      c,
      NonEmptyList.of(
        rankingLengthCodec,
        cadenceCodec,
        userSelectorCodec,
        maxPositionCodec,
        fieldSelectorCodec,
        sampleSelectorCodec,
        andSelectorCodec,
        orSelectorCodec,
        notSelectorCodec,
        acceptSelectorCodec
      )
    )
  )

  def decodeChain[A](c: ACursor, decoders: NonEmptyList[Decoder[? <: A]]): Either[DecodingFailure, A] = {
    NonEmptyList.fromList(decoders.tail) match {
      case None => decoders.head.tryDecode(c)
      case Some(tail) =>
        decoders.head.tryDecode(c) match {
          case Left(_)      => decodeChain(c, tail)
          case Right(value) => Right(value)
        }
    }
  }

  given selectorEncoder: Encoder[Selector] = Encoder.instance {
    case f: FieldSelector               => fieldSelectorCodec(f)
    case s: SampleSelector              => sampleSelectorEncoder(s)
    case a: AndSelector                 => andSelectorCodec(a)
    case o: OrSelector                  => orSelectorCodec(o)
    case n: NotSelector                 => notSelectorCodec(n)
    case a: AcceptSelector              => acceptSelectorCodec(a)
    case m: InteractionPositionSelector => maxPositionCodec(m)
    case r: RankingLengthSelector       => rankingLengthCodec(r)
    case u: UserSelector                => userSelectorCodec(u)
    case c: CadenceSelector             => cadenceCodec(c)
  }
  given selectorCodec: Codec[Selector] = Codec.from(selectorDecoder, selectorEncoder)

  given andSelectorCodec: Codec[AndSelector] = deriveCodec
  given orSelectorCodec: Codec[OrSelector]   = deriveCodec
  given notSelectorCodec: Codec[NotSelector] = deriveCodec
}
