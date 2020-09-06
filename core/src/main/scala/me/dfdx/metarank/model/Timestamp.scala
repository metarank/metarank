package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

import scala.util.Try

case class Timestamp(value: Long) extends AnyVal

object Timestamp {
  implicit val tsCodec = Codec.from(
    decodeA = Decoder.decodeString
      .emapTry(str => Try(str.toLong))
      .map(Timestamp.apply)
      .ensure(_.value > 0L, "timestamp cannot be negative")
      .ensure(!_.value.isNaN, "timestamp cannot be NaN"),
    encodeA = Encoder.instance[Timestamp](ts => Encoder.encodeString(ts.value.toString))
  )
}
