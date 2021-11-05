package ai.metarank.model

import io.circe.Decoder

case class UserId(value: String)

object UserId {
  implicit val userDecoder: Decoder[UserId] =
    Decoder.decodeString.ensure(_.nonEmpty, "user id cannot be empty").map(UserId.apply)

}
