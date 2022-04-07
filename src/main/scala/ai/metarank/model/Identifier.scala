package ai.metarank.model

import io.circe.{Decoder, Encoder}

sealed trait Identifier {
  def value: String
}

object Identifier {
  case class UserId(value: String) extends Identifier

  object UserId {
    implicit val userEncoder: Encoder[UserId] = Encoder.encodeString.contramap(_.value)
    implicit val userDecoder: Decoder[UserId] =
      Decoder.decodeString.ensure(_.nonEmpty, "user id cannot be empty").map(UserId.apply)

  }

  case class ItemId(value: String) extends Identifier

  object ItemId {
    implicit val itemEncoder: Encoder[ItemId] = Encoder.encodeString.contramap(_.value)
    implicit val itemDecoder: Decoder[ItemId] =
      Decoder.decodeString.ensure(_.nonEmpty, "item id cannot be empty").map(ItemId.apply)

  }

  case class SessionId(value: String) extends Identifier

  object SessionId {
    implicit val sessionEncoder: Encoder[SessionId] = Encoder.encodeString.contramap(_.value)
    implicit val sessionDecoder: Decoder[SessionId] =
      Decoder.decodeString.ensure(_.nonEmpty, "session id cannot be empty").map(SessionId.apply)
  }

}
