package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Chunk, Pipe, Stream, text}
import io.circe.parser._

object JsonLineFormat extends SourceFormat with Logging {
  def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes
      .through(text.utf8.decode)
      .through(text.lines)
      .chunkN(1024)
      .flatMap(c => Stream.emits(c.toList))
      .evalMapChunk {
        case ""   => IO.pure(None)
        case json => IO.fromEither(decode[Event](json)).map(e => Option(e))
      }
      .chunkN(1024)
      .flatMap(c => Stream.emits(c.toList.flatten))
}
