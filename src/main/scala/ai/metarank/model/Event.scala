package ai.metarank.model

import ai.metarank.model.Field.NumberField
import cats.data.NonEmptyList
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.*
import ai.metarank.model.Identifier.*

import java.time.format.DateTimeFormatter
import scala.util.Try

sealed trait Event {
  def id: EventId
  def timestamp: Timestamp
  def fields: List[Field]

  lazy val fieldsMap = fields.map(f => f.name -> f).toMap
}

object Event {
  sealed trait MetadataEvent extends Event

  case class ItemEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      fields: List[Field] = Nil
  ) extends MetadataEvent

  case class UserEvent(
      id: EventId,
      user: UserId,
      timestamp: Timestamp,
      fields: List[Field] = Nil
  ) extends MetadataEvent

  sealed trait FeedbackEvent extends Event {
    def user: Option[UserId]
    def session: Option[SessionId]
  }

  case class RankingEvent(
      id: EventId,
      timestamp: Timestamp,
      user: Option[UserId],
      session: Option[SessionId],
      fields: List[Field] = Nil,
      items: NonEmptyList[RankItem]
  ) extends FeedbackEvent

  case class InteractionEvent(
      id: EventId,
      item: ItemId,
      timestamp: Timestamp,
      ranking: Option[EventId] = None,
      user: Option[UserId],
      session: Option[SessionId],
      `type`: String,
      fields: List[Field] = Nil
  ) extends FeedbackEvent

  case class RankItem(id: ItemId, fields: List[Field] = Nil, label: Option[Int] = None)
  object RankItem {
    def apply(id: ItemId, relevancy: Double) = new RankItem(id, List(NumberField("relevancy", relevancy)))
  }

  object EventCodecs {
    val dateTimeFormat = DateTimeFormatter.ISO_DATE_TIME
    given timestampCodec: Codec[Timestamp] = Codec.from(
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
    given relevancyEncoder: Encoder[RankItem] = deriveEncoder
    given relevancyDecoder: Decoder[RankItem] = Decoder.instance(c =>
      for {
        id     <- c.downField("id").as[ItemId]
        rel    <- c.downField("relevancy").as[Option[Double]]
        fields <- c.downField("fields").as[Option[List[Field]]]
        label  <- c.downField("label").as[Option[Int]]
      } yield {
        RankItem(id, rel.toList.map(r => NumberField("relevancy", r)) ++ fields.toList.flatten, label)
      }
    )
    given relevancyCodec: Codec[RankItem] = Codec.from(relevancyDecoder, relevancyEncoder)

    given itemCodec: Codec[ItemEvent] = Codec.from(
      decodeA = Decoder.instance(c =>
        for {
          id        <- c.downField("id").as[EventId]
          item      <- c.downField("item").as[ItemId]
          timestamp <- c.downField("timestamp").as[Timestamp]
          fields    <- c.getOrElse[List[Field]]("fields")(Nil)
        } yield ItemEvent(id = id, item = item, timestamp = timestamp, fields = fields)
      ),
      encodeA = deriveEncoder
    )
    given userCodec: Codec[UserEvent] = Codec.from(
      decodeA = Decoder.instance(c =>
        for {
          id        <- c.downField("id").as[EventId]
          user      <- c.downField("user").as[UserId]
          timestamp <- c.downField("timestamp").as[Timestamp]
          fields    <- c.getOrElse[List[Field]]("fields")(Nil)
        } yield UserEvent(id = id, user = user, timestamp = timestamp, fields = fields)
      ),
      encodeA = deriveEncoder
    )
    given rankingCodec: Codec[RankingEvent] = Codec.from(
      decodeA = Decoder.instance(c =>
        for {
          id        <- c.downField("id").as[EventId]
          timestamp <- c.downField("timestamp").as[Timestamp]
          user      <- c.downField("user").as[Option[UserId]]
          session   <- c.downField("session").as[Option[SessionId]]
          fields    <- c.getOrElse[List[Field]]("fields")(Nil)
          items     <- c.downField("items").as[NonEmptyList[RankItem]]
        } yield RankingEvent(
          id = id,
          timestamp = timestamp,
          user = user,
          session = session,
          fields = fields,
          items = items
        )
      ),
      encodeA = deriveEncoder
    )
    given interactionCodec: Codec[InteractionEvent] = Codec.from(
      decodeA = Decoder.instance(c =>
        for {
          id        <- c.downField("id").as[EventId]
          item      <- c.downField("item").as[ItemId]
          timestamp <- c.downField("timestamp").as[Timestamp]
          ranking   <- c.downField("ranking").as[Option[EventId]]
          user      <- c.downField("user").as[Option[UserId]]
          session   <- c.downField("session").as[Option[SessionId]]
          tpe       <- c.downField("type").as[String]
          fields    <- c.getOrElse[List[Field]]("fields")(Nil)
        } yield InteractionEvent(
          id = id,
          item = item,
          timestamp = timestamp,
          ranking = ranking,
          user = user,
          session = session,
          `type` = tpe,
          fields = fields
        )
      ),
      encodeA = deriveEncoder
    )
  }

  import EventCodecs.itemCodec
  import EventCodecs.userCodec
  import EventCodecs.rankingCodec
  import EventCodecs.interactionCodec

  given eventEncoder: Encoder[Event] = Encoder.instance {
    case e: ItemEvent        => itemCodec(e).deepMerge(Json.obj("event" -> Json.fromString("item")))
    case e: UserEvent        => userCodec(e).deepMerge(Json.obj("event" -> Json.fromString("user")))
    case e: RankingEvent     => rankingCodec(e).deepMerge(Json.obj("event" -> Json.fromString("ranking")))
    case e: InteractionEvent => interactionCodec(e).deepMerge(Json.obj("event" -> Json.fromString("interaction")))
  }
  given eventDecoder: Decoder[Event] = Decoder.instance(c =>
    c.downField("event").as[String] match {
      case Left(error) => Left(DecodingFailure(s"required field 'event' missing in JSON", c.history))
      case Right("metadata") | Right("item") => itemCodec.tryDecode(c)
      case Right("user")                     => userCodec.tryDecode(c)
      case Right("ranking")                  => rankingCodec.tryDecode(c)
      case Right("interaction")              => interactionCodec.tryDecode(c)
      case Right(other) => Left(DecodingFailure(s"event type '$other' is not supported", c.history))
    }
  )
  given eventCodec: Codec[Event] = Codec.from(eventDecoder, eventEncoder)
}
