package ai.metarank.config

import ai.metarank.config.SourceFormat.FormatReader
import ai.metarank.model.Event
import ai.metarank.source.format.JsonFormat
import ai.metarank.source.format.SnowplowFormat.{SnowplowJsonFormat, SnowplowTSVFormat}
import io.circe.{Codec, Decoder, Json}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import scala.util.{Failure, Success}

trait SourceFormat {
  def reader(stream: InputStream): FormatReader
  def parse(bytes: Array[Byte]): Either[Throwable, Option[Event]]
}
object SourceFormat {

  trait FormatReader {
    def next(): Either[Throwable, Option[Event]]

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

  implicit val sourceFormatDecoder: Decoder[SourceFormat] = Decoder.decodeString.emapTry {
    case "json"          => Success(JsonFormat)
    case "snowplow"      => Success(SnowplowTSVFormat)
    case "snowplow:tsv"  => Success(SnowplowTSVFormat)
    case "snowplow:json" => Success(SnowplowJsonFormat)
    case other => Failure(new IllegalArgumentException(s"format $other is not supported. try json/snowplow instead."))
  }
}
