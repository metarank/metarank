package ai.metarank.model

import io.circe.Decoder

case class SessionId(value: String)

object SessionId {
  implicit val sessionDecoder: Decoder[SessionId] =
    Decoder.decodeString.ensure(_.nonEmpty, "session id cannot be empty").map(SessionId.apply)
}
