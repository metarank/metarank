package ai.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.extras.Configuration
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto._
import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveConfiguredDecoder, deriveConfiguredEncoder}
import ai.metarank.model.Identifier._

import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait Event {
  def id: EventId
  def timestamp: Timestamp
  def fields: List[Field]
  def env: Env

  lazy val fieldsMap = fields.map(f => f.name -> f).toMap
}

object Event {
  sealed trait MetadataEvent extends Event

  case class ItemEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      fields: List[Field] = Nil,
      env: Env = Env("default")
  ) extends MetadataEvent

  case class UserEvent(
      id: EventId,
      user: UserId,
      timestamp: Timestamp,
      fields: List[Field] = Nil,
      env: Env = Env("default")
  ) extends MetadataEvent

  sealed trait FeedbackEvent extends Event {
    def user: UserId
    def session: Option[SessionId]
  }

  case class RankingEvent(
      id: EventId,
      timestamp: Timestamp,
      user: UserId,
      session: Option[SessionId],
      fields: List[Field] = Nil,
      items: NonEmptyList[ItemRelevancy],
      env: Env = Env("default")
  ) extends FeedbackEvent

  case class InteractionEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      ranking: Option[EventId] = None,
      user: UserId,
      session: Option[SessionId],
      `type`: String,
      fields: List[Field] = Nil,
      env: Env = Env("default")
  ) extends FeedbackEvent

  case class ItemRelevancy(id: ItemId, relevancy: Option[Double] = None)
  object ItemRelevancy {
    def apply(id: ItemId, relevancy: Double) = new ItemRelevancy(id, Some(relevancy))
  }

  object EventCodecs {
    val dateTimeFormat = DateTimeFormatter.ISO_DATE_TIME
    implicit val timestampCodec: Codec[Timestamp] = Codec.from(
      decodeA = Decoder.decodeLong
        .or(
          Decoder
            .decodeZonedDateTimeWithFormatter(dateTimeFormat)
            .map(_.toInstant.toEpochMilli)
            .or(Decoder.decodeString.emapTry(str => Try(str.toLong)))
        )
        .map(Timestamp.apply),
      encodeA = Encoder.encodeString.contramap(_.ts.toString)
    )
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
      case Left(error) => Left(DecodingFailure(s"required field 'event' missing in JSON", c.history))
      case Right("metadata") | Right("item") => itemCodec.tryDecode(c)
      case Right("user")                     => userCodec.tryDecode(c)
      case Right("ranking")                  => rankingCodec.tryDecode(c)
      case Right("interaction")              => interactionCodec.tryDecode(c)
      case Right(other) => Left(DecodingFailure(s"event type '$other' is not supported", c.history))
    }
  )
  implicit val eventCodec: Codec[Event] = Codec.from(eventDecoder, eventEncoder)
}
