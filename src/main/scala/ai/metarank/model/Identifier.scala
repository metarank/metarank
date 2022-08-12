package ai.metarank.model

import io.circe.{Codec, Decoder, Encoder}

sealed trait Identifier extends Any {
  def value: String
}

object Identifier {
  case class UserId(value: String) extends AnyVal with Identifier

  object UserId {
    implicit val userEncoder: Encoder[UserId] = Encoder.encodeString.contramap(_.value)
    implicit val userDecoder: Decoder[UserId] =
      Decoder.decodeString.ensure(_.nonEmpty, "user id cannot be empty").map(UserId.apply)
    implicit val userCodec: Codec[UserId] = Codec.from(userDecoder, userEncoder)
  }

  case class ItemId(value: String) extends AnyVal with Identifier

  object ItemId {
    implicit val itemEncoder: Encoder[ItemId] = Encoder.encodeString.contramap(_.value)
    implicit val itemDecoder: Decoder[ItemId] =
      Decoder.decodeString.ensure(_.nonEmpty, "item id cannot be empty").map(ItemId.apply)
    implicit val itemCodec: Codec[ItemId] = Codec.from(itemDecoder, itemEncoder)
  }

  case class SessionId(value: String) extends AnyVal with Identifier

  object SessionId {
    implicit val sessionEncoder: Encoder[SessionId] = Encoder.encodeString.contramap(_.value)
    implicit val sessionDecoder: Decoder[SessionId] =
      Decoder.decodeString.ensure(_.nonEmpty, "session id cannot be empty").map(SessionId.apply)
  }

}
