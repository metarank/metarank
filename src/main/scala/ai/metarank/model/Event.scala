package ai.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.extras.Configuration
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.findify.featury.model.Timestamp
import io.circe.generic.semiauto._
import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveConfiguredDecoder, deriveConfiguredEncoder}

sealed trait Event {
  def id: EventId
  def timestamp: Timestamp
  def fields: List[Field]
  def tenant: String

  lazy val fieldsMap = fields.map(f => f.name -> f).toMap
}

object Event {
  sealed trait MetadataEvent extends Event

  case class ItemEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      fields: List[Field] = Nil,
      tenant: String = "default"
  ) extends MetadataEvent

  case class UserEvent(
      id: EventId,
      user: UserId,
      timestamp: Timestamp,
      fields: List[Field] = Nil,
      tenant: String = "default"
  ) extends MetadataEvent

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
    implicit val conf                                 = Configuration.default.withDefaults
    implicit val relevancyCodec: Codec[ItemRelevancy] = deriveCodec

    implicit val itemCodec: Codec[ItemEvent]               = deriveConfiguredCodec
    implicit val userCodec: Codec[UserEvent]               = deriveConfiguredCodec
    implicit val rankingCodec: Codec[RankingEvent]         = deriveConfiguredCodec
    implicit val interactionCodec: Codec[InteractionEvent] = deriveConfiguredCodec
  }

  import EventCodecs.itemCodec
  import EventCodecs.userCodec
  import EventCodecs.rankingCodec
  import EventCodecs.interactionCodec

  implicit val conf = Configuration.default
    .withDiscriminator("event")
    .withKebabCaseMemberNames
    .copy(transformConstructorNames = {
      case "ItemEvent"        => "item"
      case "UserEvent"        => "user"
      case "RankingEvent"     => "ranking"
      case "InteractionEvent" => "interaction"
    })

  implicit val eventEncoder: Encoder[Event] = deriveConfiguredEncoder
  implicit val eventDecoder: Decoder[Event] = Decoder.instance(c =>
    c.downField("event").as[String] match {
      case Left(error)                       => Left(error)
      case Right("metadata") | Right("item") => itemCodec.tryDecode(c)
      case Right("user")                     => userCodec.tryDecode(c)
      case Right("ranking")                  => rankingCodec.tryDecode(c)
      case Right("interaction")              => interactionCodec.tryDecode(c)
      case Right(other) => Left(DecodingFailure(s"event type '$other' is not supported", c.history))
    }
  )
  implicit val eventCodec: Codec[Event] = Codec.from(eventDecoder, eventEncoder)
}
