package ai.metarank.config

import ai.metarank.model.Event
import ai.metarank.source.format.JsonFormat
import ai.metarank.source.format.SnowplowFormat.{SnowplowJSONFormat, SnowplowTSVFormat}
import cats.effect.IO
import fs2.Pipe
import io.circe.{Codec, Decoder, Json}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import scala.util.{Failure, Success}

trait SourceFormat {
  def parse: Pipe[IO, Byte, Event]
}

object SourceFormat {

  implicit val sourceFormatDecoder: Decoder[SourceFormat] = Decoder.decodeString.emapTry {
    case "json"          => Success(JsonFormat)
    case "snowplow"      => Success(SnowplowTSVFormat)
    case "snowplow:tsv"  => Success(SnowplowTSVFormat)
    case "snowplow:json" => Success(SnowplowJSONFormat)
    case other => Failure(new IllegalArgumentException(s"format $other is not supported. try json/snowplow instead."))
  }
}
