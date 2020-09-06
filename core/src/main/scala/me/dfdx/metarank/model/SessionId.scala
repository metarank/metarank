package me.dfdx.metarank.model

import io.circe.{Decoder, Encoder}
import io.circe.parser._

case class SessionId(value: String) extends AnyVal

object SessionId {
  implicit val sessionEncoder = Encoder.instance[SessionId](user => Encoder.encodeString(user.value))
  implicit val sessionDecoder = Decoder
    .instance[SessionId](_.as[String].map(SessionId.apply))
    .ensure(_.value.nonEmpty, "session id cannot be empty")
}
