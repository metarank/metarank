package ai.metarank.model

import io.circe.{Decoder, Encoder}

case class UserId(value: String) extends AnyVal

object UserId {
  implicit val userEncoder: Encoder[UserId] = Encoder.encodeString.contramap(_.value)
  implicit val userDecoder: Decoder[UserId] =
    Decoder.decodeString.ensure(_.nonEmpty, "user id cannot be empty").map(UserId.apply)

}
