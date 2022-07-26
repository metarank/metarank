package ai.metarank.model

import ai.metarank.model.Identifier.{ItemId, UserId}
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.findify.featury.model.Key.Tenant
import io.circe.generic.semiauto._

sealed trait FieldId {
  def tenant: Tenant
  def field: String
}

object FieldId {
  import io.findify.featury.model.json.KeyJson._
  case class ItemFieldId(tenant: Tenant, item: ItemId, field: String) extends FieldId
  case class UserFieldId(tenant: Tenant, user: UserId, field: String) extends FieldId

  implicit val itemFieldCodec: Codec[ItemFieldId] = deriveCodec[ItemFieldId]
  implicit val userFieldCodec: Codec[UserFieldId] = deriveCodec[UserFieldId]
  implicit val fieldIdEncoder: Encoder[FieldId] = Encoder.instance {
    case u: UserFieldId => userFieldCodec(u)
    case i: ItemFieldId => itemFieldCodec(i)
  }

  implicit val fieldIdDecoder: Decoder[FieldId] = Decoder.instance(c =>
    (c.downField("user").as[String], c.downField("item").as[String]) match {
      case (Right(user), Left(_)) => userFieldCodec.tryDecode(c)
      case (Left(_), Right(item)) => itemFieldCodec.tryDecode(c)
      case (_, _)                 => Left(DecodingFailure("user/item fields missing", c.history))
    }
  )

  implicit val fieldIdCodec: Codec[FieldId] = Codec.from(fieldIdDecoder, fieldIdEncoder)
}
