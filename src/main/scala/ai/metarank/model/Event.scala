package ai.metarank.model

import io.circe.generic.extras.Configuration
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.findify.featury.model.Timestamp
import io.circe.generic.semiauto._
import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveConfiguredDecoder}

sealed trait Event {
  def id: EventId
  def timestamp: Timestamp
  def fields: List[Field]
  def tenant: String
}

object Event {
  case class MetadataEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      fields: List[Field],
      tenant: String
  ) extends Event

  sealed trait FeedbackEvent extends Event {
    def user: UserId
    def session: SessionId
  }

  case class RankingEvent(
      id: EventId,
      timestamp: Timestamp,
      user: UserId,
      session: SessionId,
      fields: List[Field],
      items: List[ItemRelevancy],
      tenant: String
  ) extends FeedbackEvent

  case class InteractionEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      ranking: EventId,
      user: UserId,
      session: SessionId,
      `type`: String,
      fields: List[Field],
      tenant: String
  ) extends FeedbackEvent

  case class ItemRelevancy(id: ItemId, relevancy: Double)

  implicit val relevancyCodec: Codec[ItemRelevancy]      = deriveCodec
  implicit val metadataCodec: Codec[MetadataEvent]       = deriveCodec
  implicit val rankingCodec: Codec[RankingEvent]         = deriveCodec
  implicit val interactionCodec: Codec[InteractionEvent] = deriveCodec

  implicit val conf = Configuration.default
    .withDiscriminator("event")
    .withKebabCaseMemberNames
    .copy(transformConstructorNames = {
      case "MetadataEvent"    => "metadata"
      case "RankingEvent"     => "ranking"
      case "InteractionEvent" => "interaction"
    })

  implicit val eventCodec: Codec[Event] = deriveConfiguredCodec[Event]

}
