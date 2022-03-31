package ai.metarank.model

import cats.data.NonEmptyList
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

  lazy val fieldsMap = fields.map(f => f.name -> f).toMap
}

object Event {
  case class MetadataEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      fields: List[Field] = Nil,
      tenant: String = "default"
  ) extends Event {}

  sealed trait FeedbackEvent extends Event {
    def user: UserId
    def session: SessionId
  }

  case class RankingEvent(
      id: EventId,
      timestamp: Timestamp,
      user: UserId,
      session: SessionId,
      fields: List[Field] = Nil,
      items: NonEmptyList[ItemRelevancy],
      tenant: String = "default"
  ) extends FeedbackEvent

  case class InteractionEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      ranking: EventId,
      user: UserId,
      session: SessionId,
      `type`: String,
      fields: List[Field] = Nil,
      tenant: String = "default"
  ) extends FeedbackEvent

  case class ItemRelevancy(id: ItemId, relevancy: Option[Double] = None)
  object ItemRelevancy {
    def apply(id: ItemId, relevancy: Double) = new ItemRelevancy(id, Some(relevancy))
  }

  object EventCodecs {
    implicit val conf                                      = Configuration.default.withDefaults
    implicit val relevancyCodec: Codec[ItemRelevancy]      = deriveCodec
    implicit val metadataCodec: Codec[MetadataEvent]       = deriveConfiguredCodec
    implicit val rankingCodec: Codec[RankingEvent]         = deriveConfiguredCodec
    implicit val interactionCodec: Codec[InteractionEvent] = deriveConfiguredCodec
  }

  import EventCodecs.metadataCodec
  import EventCodecs.rankingCodec
  import EventCodecs.interactionCodec
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
