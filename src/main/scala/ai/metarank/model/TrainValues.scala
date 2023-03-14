package ai.metarank.model
import ai.metarank.model.Event.{ItemEvent, UserEvent}
import ai.metarank.model.Identifier.{ItemId, UserId}
import cats.data.NonEmptyList
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, FailedCursor, HCursor, Json, JsonObject}
import io.circe.generic.semiauto._

sealed trait TrainValues {
  def id: EventId
}

object TrainValues {
  case class ClickthroughValues(ct: Clickthrough, values: List[ItemValue]) extends TrainValues {
    val id = ct.id
  }
  case class ItemValues(id: EventId, item: ItemId, timestamp: Timestamp, fields: List[Field] = Nil) extends TrainValues
  object ItemValues {
    def apply(e: ItemEvent) = new ItemValues(e.id, e.item, e.timestamp, e.fields)
  }
  case class UserValues(id: EventId, user: UserId, timestamp: Timestamp, fields: List[Field] = Nil) extends TrainValues
  object UserValues {
    def apply(e: UserEvent) = new UserValues(e.id, e.user, e.timestamp, e.fields)
  }

  implicit val ctvJsonCodec: Codec[ClickthroughValues] = deriveCodec[ClickthroughValues]
  implicit val itemJsonCodec: Codec[ItemValues]        = deriveCodec[ItemValues]
  implicit val userValuesCodec: Codec[UserValues]      = deriveCodec[UserValues]

  implicit val trainEncoder: Encoder[TrainValues] = Encoder.instance {
    case t: ClickthroughValues => ctvJsonCodec.apply(t).deepMerge(withType("ct"))
    case t: ItemValues         => itemJsonCodec.apply(t).deepMerge(withType("item"))
    case t: UserValues         => userValuesCodec.apply(t).deepMerge(withType("user"))
  }

  def withType(tpe: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(tpe))))

  implicit val trainDecoder: Decoder[TrainValues] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(error)   => Left(error)
      case Right("ct")   => ctvJsonCodec.tryDecode(c)
      case Right("item") => itemJsonCodec.tryDecode(c)
      case Right("user") => userValuesCodec.tryDecode(c)
      case Right(other)  => Left(DecodingFailure(s"train event type '$other' is not supported", c.history))
    }
  )

  implicit val trainCodec: Codec[TrainValues] = Codec.from(trainDecoder, trainEncoder)
}
