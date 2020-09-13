package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class UserAgent(value: String) extends AnyVal

object UserAgent {
  implicit val uaCodec = Codec.from(
    decodeA = Decoder.decodeString.map(UserAgent.apply).ensure(_.value.nonEmpty, "user agent cannot be empty"),
    encodeA = Encoder.instance[UserAgent](id => Encoder.encodeString(id.value))
  )

}
