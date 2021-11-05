package ai.metarank.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.findify.featury.model.Timestamp
import io.circe.generic.semiauto._

sealed trait Event {
  def id: EventId
  def timestamp: Timestamp
  def fields: List[Field]
  def tenant: Option[String]
}

object Event {
  case class MetadataEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      fields: List[Field],
      tenant: Option[String] = None
  ) extends Event

  case class ImpressionEvent(
      id: EventId,
      timestamp: Timestamp,
      user: UserId,
      session: SessionId,
      fields: List[Field],
      items: List[ItemRelevancy],
      tenant: Option[String] = None
  ) extends Event

  case class InteractionEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      impression: EventId,
      user: UserId,
      session: SessionId,
      `type`: String,
      fields: List[Field],
      tenant: Option[String] = None
  ) extends Event

  case class ItemRelevancy(id: ItemId, relevancy: Double)

  implicit val relevancyCodec: Codec[ItemRelevancy]      = deriveCodec
  implicit val metadataCodec: Codec[MetadataEvent]       = deriveCodec
  implicit val impressionCodec: Codec[ImpressionEvent]   = deriveCodec
  implicit val interactionCodec: Codec[InteractionEvent] = deriveCodec

  implicit val eventDecoder: Decoder[Event] = Decoder.instance(c =>
    interactionCodec.apply(c) match {
      case Right(value) => Right(value)
      case Left(interactionError) =>
        impressionCodec.apply(c) match {
          case Right(value) => Right(value)
          case Left(impressionError) =>
            metadataCodec.apply(c) match {
              case Right(value) => Right(value)
              case Left(metadataError) =>
                Left(
                  DecodingFailure(
                    s"cannot decode event: metadata=$metadataError impression=$impressionError interac=$interactionError",
                    c.history
                  )
                )
            }
        }
    }
  )

  implicit val eventEncoder: Encoder[Event] = Encoder.instance {
    case e: MetadataEvent    => metadataCodec.apply(e)
    case e: ImpressionEvent  => impressionCodec.apply(e)
    case e: InteractionEvent => interactionCodec.apply(e)
  }

}
