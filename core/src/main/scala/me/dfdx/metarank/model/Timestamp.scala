package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class Timestamp(value: Long) extends AnyVal

object Timestamp {
  implicit val tsCodec = Codec.from(
    decodeA = Decoder.decodeString.map(str => Timestamp(str.toLong)),
    encodeA = Encoder.instance[Timestamp](ts => Encoder.encodeString(ts.value.toString))
  )
}
