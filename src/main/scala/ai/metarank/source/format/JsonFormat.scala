package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Pull, Stream}

object JsonFormat extends SourceFormat with Logging {

  override def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes.pull.peek1.flatMap {
      case Some(('[', tail)) => tail.through(JsonArrayFormat.parse).pull.echo
      case Some((_, tail))   => tail.through(JsonLineFormat.parse).pull.echo
      case None              => Pull.done
    }.stream

}
