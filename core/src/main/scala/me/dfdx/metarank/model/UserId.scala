package me.dfdx.metarank.model

import io.circe.{Decoder, Encoder}
import io.circe.parser._

case class UserId(value: String) extends AnyVal

object UserId {
  implicit val userEncoder = Encoder.instance[UserId](user => Encoder.encodeString(user.value))
  implicit val userDecoder = Decoder
    .instance[UserId](_.as[String].map(UserId.apply))
    .ensure(_.value.nonEmpty, "User id cannot be empty")
}
