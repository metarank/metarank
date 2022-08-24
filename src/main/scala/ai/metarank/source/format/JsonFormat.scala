package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Pull, Stream}
import fs2.Chunk
import io.circe.Json
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.AsyncParser.{Mode, UnwrapArray, ValueStream}

object JsonFormat extends SourceFormat with Logging {
  import io.circe.jawn.CirceSupportParser.facade

  override def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes.pull.peek1.flatMap {
      case Some(('[', tail)) => tail.through(parse(UnwrapArray)).pull.echo
      case Some((_, tail))   => tail.through(parse(ValueStream)).pull.echo
      case None              => Pull.done
    }.stream

  private def parse(mode: Mode): Pipe[IO, Byte, Event] = bytes =>
    bytes
      .scanChunks(AsyncParser[Json](mode))((parser, next) => {
        parser.absorb(next.toByteBuffer) match {
          case Left(value) =>
            logger.error(s"Cannot parse json input: '${new String(next.toArray)}'", value)
            throw value
          case Right(value) => (parser, Chunk.seq(value))
        }
      })
      .flatMap(_.as[Event] match {
        case Left(error)  => Stream.raiseError[IO](error)
        case Right(event) => Stream.emit(event)
      })
      .chunkN(1024)
      .flatMap(x => Stream.emits(x.toList))
}
