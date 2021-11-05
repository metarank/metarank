package ai.metarank.model

import io.circe.Decoder

case class ItemId(value: String)

object ItemId {
  implicit val itemDecoder: Decoder[ItemId] =
    Decoder.decodeString.ensure(_.nonEmpty, "item id cannot be empty").map(ItemId.apply)

}
