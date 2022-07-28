package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Pipe
import fs2.Stream

object JsonFormat extends SourceFormat with Logging {
  override def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes.head.flatMap {
      case '[' =>
        Stream
          .exec(IO(logger.info("auto-detected a JSON-array file format")))
          .append(bytes)
          .through(JsonArrayFormat.parse)
      case other =>
        Stream
          .exec(IO(logger.info("auto-detected a JSON-line file format")))
          .append(bytes)
          .through(JsonLineFormat.parse)

    }

}
