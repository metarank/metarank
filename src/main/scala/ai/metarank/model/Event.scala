package ai.metarank.model

import io.circe.{Decoder, DecodingFailure}
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

  implicit val relevancyDecoder: Decoder[ItemRelevancy]      = deriveDecoder
  implicit val metadataDecoder: Decoder[MetadataEvent]       = deriveDecoder
  implicit val impressionDecoder: Decoder[ImpressionEvent]   = deriveDecoder
  implicit val interactionDecoder: Decoder[InteractionEvent] = deriveDecoder

  implicit val eventDecoder: Decoder[Event] = Decoder.instance(c =>
    interactionDecoder.apply(c) match {
      case Right(value) => Right(value)
      case Left(interactionError) =>
        impressionDecoder.apply(c) match {
          case Right(value) => Right(value)
          case Left(impressionError) =>
            metadataDecoder.apply(c) match {
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
}
