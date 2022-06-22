package ai.metarank.config

import ai.metarank.model.Event
import io.circe.{Codec, Decoder, Json}
import io.circe.parser._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success}

sealed trait SourceFormat {
  def transform(input: Array[Byte]): Either[Throwable, Option[Event]] = {
    transform(new ByteArrayInputStream(input))
  }
  def transform(input: InputStream): Either[Throwable, Option[Event]]

  def readLine(stream: InputStream): Option[Array[Byte]] = {
    val buffer = new ByteArrayOutputStream(32)
    var count  = 0
    var next   = stream.read()
    if ((next == -1) && (count == 0)) {
      None
    } else {
      while ((next != -1) && (next != '\n')) {
        buffer.write(next)
        next = stream.read()
        count += 1
      }
      Some(buffer.toByteArray)
    }
  }
}
object SourceFormat {
  case object NativeJson extends SourceFormat {
    override def transform(input: InputStream): Either[Throwable, Option[Event]] = {
      readLine(input) match {
        case Some(line) => decode[Event](new String(line, StandardCharsets.UTF_8)).map(Option.apply)
        case None       => Right(None)
      }

    }
  }

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

  case object SnowplowTSV extends SnowplowFormat {
    override def transform(input: InputStream): Either[Throwable, Option[Event]] = {
      readLine(input) match {
        case None => Right(None)
        case Some(lineBytes) =>
          val line   = new String(lineBytes, StandardCharsets.UTF_8)
          val tokens = line.split('\t')
          tokens.lift(58) match {
            case Some(unstruct) => decode[UnstructuredData](unstruct).map(_.data).flatMap(decodeEvent)
            case None           => Right(None)
          }

      }
    }
  }

  case object SnowplowJSON extends SnowplowFormat {
    override def transform(input: InputStream): Either[Throwable, Option[Event]] = {
      readLine(input) match {
        case None => Right(None)
        case Some(lineBytes) =>
          val line = new String(lineBytes, StandardCharsets.UTF_8)
          parse(line) match {
            case Left(error) => Left(error)
            case Right(json) =>
              snowplowEnrichedEventDecoder.decodeJson(json) match {
                case Left(error)  => Left(error)
                case Right(value) => decodeEvent(value.unstruct_event.data)
              }
          }
      }
    }
  }

  implicit val sourceFormatDecoder: Decoder[SourceFormat] = Decoder.decodeString.emapTry {
    case "json"          => Success(NativeJson)
    case "snowplow"      => Success(SnowplowTSV)
    case "snowplow:tsv"  => Success(SnowplowTSV)
    case "snowplow:json" => Success(SnowplowJSON)
    case other => Failure(new IllegalArgumentException(s"format $other is not supported. try json/snowplow instead."))
  }
}
