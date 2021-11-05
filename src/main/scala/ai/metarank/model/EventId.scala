package ai.metarank.model

import io.circe.{Decoder, Encoder}

case class EventId(value: String)

object EventId {
  implicit val eventEncoder: Encoder[EventId] = Encoder.encodeString.contramap(_.value)
  implicit val eventDecoder: Decoder[EventId] =
    Decoder.decodeString.ensure(_.nonEmpty, "event id cannot be empty").map(EventId.apply)
}
