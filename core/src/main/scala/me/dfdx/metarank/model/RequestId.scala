package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class RequestId(value: String) extends AnyVal

object RequestId {
  implicit val requestCodec = Codec.from(
    decodeA = Decoder.decodeString.map(RequestId.apply).ensure(_.value.nonEmpty, "request id cannot be empty"),
    encodeA = Encoder.instance[RequestId](id => Encoder.encodeString(id.value))
  )
}
