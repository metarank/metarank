package ai.metarank.util

import ai.metarank.flow.PrintProgress
import ai.metarank.model.Event
import better.files.Resource
import cats.effect.IO
import io.circe.parser.decode

import java.util.zip.GZIPInputStream
import scala.io.Source

object RanklensEvents {
  def stream(): fs2.Stream[IO, Event] =
    fs2.io
      .readInputStream[IO](
        IO(new GZIPInputStream(Resource.my.getAsStream("/ranklens/events/events.jsonl.gz"))),
        chunkSize = 1024,
        closeAfterUse = true
      )
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.nonEmpty)
      .evalMap(line => IO.fromEither(decode[Event](line)))

  def apply(count: Int = Int.MaxValue) = {
    Source
      .fromInputStream(new GZIPInputStream(Resource.my.getAsStream("/ranklens/events/events.jsonl.gz")))
      .getLines()
      .map(line =>
        decode[Event](line) match {
          case Left(value)  => throw new IllegalArgumentException(s"illegal format: $value")
          case Right(value) => value
        }
      )
      .take(count)
      .toList
  }
}
