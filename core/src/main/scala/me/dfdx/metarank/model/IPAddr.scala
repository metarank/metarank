package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class IPAddr(value: String) extends AnyVal

object IPAddr {
  implicit val ipCodec = Codec.from(
    decodeA = Decoder.decodeString.map(IPAddr.apply).ensure(_.value.nonEmpty, "ip cannot be empty"),
    encodeA = Encoder.instance[IPAddr](id => Encoder.encodeString(id.value))
  )

}
