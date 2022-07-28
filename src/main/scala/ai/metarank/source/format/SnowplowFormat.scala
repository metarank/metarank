package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import cats.effect.IO
import fs2.{Pipe, Stream, text}
import io.circe.{Codec, Json}
import io.circe.parser._

object SnowplowFormat {
  import io.circe.generic.semiauto._
  case class MetarankEvent(schema: String, data: Json)
  case class UnstructuredData(schema: String, data: MetarankEvent)
  case class SnowplowEnrichedEvent(unstruct_event: UnstructuredData)

  implicit lazy val snowplowEnrichedEventCodec: Codec[SnowplowEnrichedEvent] = deriveCodec[SnowplowEnrichedEvent]
  implicit val unstructuredDataCodec: Codec[UnstructuredData]                = deriveCodec[UnstructuredData]
  implicit val metarankEventCodec: Codec[MetarankEvent]                      = deriveCodec[MetarankEvent]

  val supportedSchemas = Set(
    "iglu:ai.metarank/item/jsonschema/1-0-0",
    "iglu:ai.metarank/user/jsonschema/1-0-0",
    "iglu:ai.metarank/interaction/jsonschema/1-0-0",
    "iglu:ai.metarank/ranking/jsonschema/1-0-0"
  )

  def decodeEvent(eventAttempt: Either[io.circe.Error, MetarankEvent]): Stream[IO, Event] = eventAttempt match {
    case Right(event) if supportedSchemas.contains(event.schema) =>
      Event.eventCodec.decodeJson(event.data) match {
        case Right(event) => Stream.emit(event)
        case Left(error)  => Stream.raiseError[IO](error)
      }
    case Right(other) => Stream.empty
    case Left(error)  => Stream.raiseError[IO](error)
  }

  object SnowplowTSVFormat extends SourceFormat {
    override def parse: Pipe[IO, Byte, Event] = _.through(text.utf8.decode)
      .through(text.lines)
      .flatMap(line => {
        val tokens = line.split('\t')
        tokens.lift(58) match {
          case Some(unstruct) => decodeEvent(decode[UnstructuredData](unstruct).map(_.data))
          case None           => Stream.empty
        }
      })
  }

  object SnowplowJSONFormat extends SourceFormat {
    override def parse: Pipe[IO, Byte, Event] = _.through(text.utf8.decode)
      .through(text.lines)
      .flatMap(line => {
        decode[SnowplowEnrichedEvent](line) match {
          case Left(error)  => Stream.raiseError[IO](error)
          case Right(event) => decodeEvent(Right(event.unstruct_event.data))
        }
      })
  }
}
