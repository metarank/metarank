package ai.metarank.model

import io.circe.{Decoder, Encoder}

case class SessionId(value: String)

object SessionId {
  implicit val sessionEncoder: Encoder[SessionId] = Encoder.encodeString.contramap(_.value)
  implicit val sessionDecoder: Decoder[SessionId] =
    Decoder.decodeString.ensure(_.nonEmpty, "session id cannot be empty").map(SessionId.apply)
}
