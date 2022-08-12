package ai.metarank.source.format

import ai.metarank.config.SourceFormat
import ai.metarank.model.Event
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Pipe
import fs2.Stream

object JsonFormat extends SourceFormat with Logging {
  override def parse: Pipe[IO, Byte, Event] = bytes =>
    bytes.head
      .flatMap {
        case '[' =>
          Stream
            .eval(info("auto-detected a JSON-array file format"))
            .flatMap(_ => bytes)
            .through(JsonArrayFormat.parse)
        case other =>
          Stream
            .eval(info("auto-detected a JSON-line file format"))
            .flatMap(_ => bytes)
            .through(JsonLineFormat.parse)
            .mapChunks(chunk => {
              info(s"chunk size ${chunk.size}")
              chunk
            })

      }

}
