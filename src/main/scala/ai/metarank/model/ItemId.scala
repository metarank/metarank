package ai.metarank.model

import io.circe.{Decoder, Encoder}

case class ItemId(value: String) extends AnyVal

object ItemId {
  implicit val itemEncoder: Encoder[ItemId] = Encoder.encodeString.contramap(_.value)
  implicit val itemDecoder: Decoder[ItemId] =
    Decoder.decodeString.ensure(_.nonEmpty, "item id cannot be empty").map(ItemId.apply)

}
