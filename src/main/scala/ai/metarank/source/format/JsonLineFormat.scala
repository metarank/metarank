package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Stream, text}
import io.circe.parser._
import cats.implicits._

object JsonLineFormat extends SourceFormat with Logging {
  def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes
      .through(text.utf8.decode)
      .through(text.lines)
      .chunkN(1024)
      .flatMap(c => {
        c.toList.filter(_.nonEmpty).map(line => decode[Event](line)).sequence match {
          case Left(value) =>
            val nr = 1
            Stream.raiseError[IO](value)
          case Right(value) => Stream.emits(value)
        }
      })
}
