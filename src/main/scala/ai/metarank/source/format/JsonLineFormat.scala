package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.config.SourceFormat.FormatReader
import ai.metarank.model.Event
import io.circe.parser._

import java.io.InputStream
import java.nio.charset.StandardCharsets

case object JsonLineFormat extends SourceFormat {
  override def reader(stream: InputStream): SourceFormat.FormatReader = JsonLineReader(stream)

  override def parse(bytes: Array[Byte]): Either[Throwable, Option[Event]] =
    decode[Event](new String(bytes, StandardCharsets.UTF_8)).map(Option.apply)

  case class JsonLineReader(stream: InputStream) extends FormatReader {
    override def next(): Either[Throwable, Option[Event]] = {
      readLine(stream) match {
        case None       => Right(None)
        case Some(line) => JsonLineFormat.parse(line)
      }
    }
  }
}
