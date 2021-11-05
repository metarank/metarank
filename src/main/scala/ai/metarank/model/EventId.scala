package ai.metarank.model

import io.circe.Decoder

case class EventId(value: String)

object EventId {
  implicit val eventDecoder: Decoder[EventId] =
    Decoder.decodeString.ensure(_.nonEmpty, "event id cannot be empty").map(EventId.apply)
}
