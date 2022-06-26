package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.config.SourceFormat.FormatReader
import ai.metarank.model.Event
import io.circe.parser.{decode, parse}
import io.circe.{Codec, Decoder, Json}

import java.io.InputStream
import java.nio.charset.StandardCharsets

trait SnowplowFormat extends SourceFormat {
  import io.circe.generic.semiauto._
  case class MetarankEvent(schema: String, data: Json)
  case class UnstructuredData(schema: String, data: MetarankEvent)
  case class SnowplowEnrichedEvent(unstruct_event: UnstructuredData)

  implicit lazy val snowplowEnrichedEventDecoder: Decoder[SnowplowEnrichedEvent] = deriveCodec[SnowplowEnrichedEvent]
  implicit val unstructuredDataCodec: Codec[UnstructuredData]                    = deriveCodec[UnstructuredData]
  implicit val metarankEventCodec: Codec[MetarankEvent]                          = deriveCodec[MetarankEvent]

  val supportedSchemas = Set(
    "iglu:ai.metarank/item/jsonschema/1-0-0",
    "iglu:ai.metarank/user/jsonschema/1-0-0",
    "iglu:ai.metarank/interaction/jsonschema/1-0-0",
    "iglu:ai.metarank/ranking/jsonschema/1-0-0"
  )

  def decodeEvent(event: MetarankEvent): Either[Throwable, Option[Event]] = {
    if (supportedSchemas.contains(event.schema))
      Event.eventCodec.decodeJson(event.data).map(Option.apply)
    else
      Right(None)
  }
}

object SnowplowFormat {
  case object SnowplowTSVFormat extends SnowplowFormat {
    override def reader(stream: InputStream): SourceFormat.FormatReader = SnowplowTSVReader(stream)

    override def parse(bytes: Array[Byte]): Either[Throwable, Option[Event]] = {
      val line   = new String(bytes, StandardCharsets.UTF_8)
      val tokens = line.split('\t')
      tokens.lift(58) match {
        case Some(unstruct) => decode[UnstructuredData](unstruct).map(_.data).flatMap(decodeEvent)
        case None           => Right(None)
      }
    }

    case class SnowplowTSVReader(stream: InputStream) extends FormatReader {
      override def next(): Either[Throwable, Option[Event]] = readLine(stream) match {
        case Some(bytes) => SnowplowTSVFormat.parse(bytes)
        case None        => Right(None)
      }
    }
  }

  case object SnowplowJsonFormat extends SnowplowFormat {
    import io.circe.parser.{parse => cparse}
    override def reader(stream: InputStream): FormatReader = SnowplowJsonReader(stream)

    override def parse(bytes: Array[Byte]): Either[Throwable, Option[Event]] = {
      val line = new String(bytes, StandardCharsets.UTF_8)
      cparse(line) match {
        case Left(error) => Left(error)
        case Right(json) =>
          snowplowEnrichedEventDecoder.decodeJson(json) match {
            case Left(error)  => Left(error)
            case Right(value) => decodeEvent(value.unstruct_event.data)
          }
      }

    }

    case class SnowplowJsonReader(stream: InputStream) extends FormatReader {
      override def next(): Either[Throwable, Option[Event]] = readLine(stream) match {
        case Some(value) => SnowplowJsonFormat.parse(value)
        case None        => Right(None)
      }
    }

  }
}
