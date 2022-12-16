package ai.metarank.model

import io.circe.{Decoder, Encoder}

import java.util.UUID

case class EventId(value: String) {
  override def toString: String = value
}

object EventId {
  def randomUUID = EventId(UUID.randomUUID().toString)
  implicit val eventEncoder: Encoder[EventId] = Encoder.encodeString.contramap(_.value)
  implicit val eventDecoder: Decoder[EventId] =
    Decoder.decodeString.ensure(_.nonEmpty, "event id cannot be empty").map(EventId.apply)
}
