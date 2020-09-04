package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class RequestId(value: String) extends AnyVal

object RequestId {
  implicit val requestCodec = Codec.from(
    decodeA = Decoder.decodeString.map(RequestId.apply),
    encodeA = Encoder.instance[RequestId](id => Encoder.encodeString(id.value))
  )
}
