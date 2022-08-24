package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import cats.effect.IO
import fs2.{Chunk, Pipe, Stream}
import io.circe.Json
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.UnwrapArray

object JsonArrayFormat extends SourceFormat {
  import io.circe.jawn.CirceSupportParser.facade

  override def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes
      .scanChunks(AsyncParser[Json](UnwrapArray))((parser, next) => {
        parser.absorb(next.toByteBuffer) match {
          case Left(value)  => throw value
          case Right(value) => (parser, Chunk.seq(value))
        }
      })
      .flatMap(_.as[Event] match {
        case Left(error)  => Stream.raiseError[IO](error)
        case Right(event) => Stream.emit(event)
      })
}
