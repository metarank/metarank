package ai.metarank.util

import ai.metarank.model.Event
import better.files.Resource
import io.circe.parser.decode

import java.util.zip.GZIPInputStream
import scala.io.Source

object RanklensEvents {
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
