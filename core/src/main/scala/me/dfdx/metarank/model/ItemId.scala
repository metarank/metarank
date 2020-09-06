package me.dfdx.metarank.model

import io.circe.{Codec, Decoder, Encoder}

case class ItemId(id: String) extends AnyVal

object ItemId {
  implicit val itemCodec = Codec.from(
    decodeA = Decoder.decodeString.map(ItemId.apply).ensure(_.id.nonEmpty, "item id cannot be empty"),
    encodeA = Encoder.instance[ItemId](id => Encoder.encodeString(id.id))
  )

}
