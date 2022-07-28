package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import cats.effect.IO
import fs2.{Pipe, text}
import io.circe.parser._

object JsonLineFormat extends SourceFormat {
  def parse: Pipe[IO, Byte, Event] =
    _.through(text.utf8.decode)
      .through(text.lines)
      .flatMap {
        case empty if empty.isEmpty => fs2.Stream.empty
        case json =>
          decode[Event](json) match {
            case Left(error)  => fs2.Stream.raiseError[IO](error)
            case Right(event) => fs2.Stream.emit(event)
          }
      }
}
