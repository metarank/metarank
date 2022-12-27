package ai.metarank.tool

import ai.metarank.model.{Event, EventId, Timestamp}
import ai.metarank.model.Event.InteractionEvent
import ai.metarank.model.Identifier.{ItemId, UserId}
import cats.effect.IO
import fs2.Stream

import java.io.InputStream
import fs2.io._
import fs2.text._

object MovielensRatingsSource {
  def fromInputStream(ratings: InputStream): Stream[IO, Event] = {
    readInputStream[IO](IO.pure(ratings), 10 * 1024)
      .through(utf8.decode[IO])
      .through(lines[IO])
      .filter(_.nonEmpty)
      .flatMap(line => {
        val tokens = line.split("::")
        if (tokens.length == 4) {
          Stream(
            InteractionEvent(
              id = EventId.randomUUID,
              item = ItemId(tokens(1)),
              timestamp = Timestamp(tokens(3).toLong * 1000),
              user = Some(UserId(tokens(0))),
              `type` = "click",
              session = None
            )
          )
        } else {
          Stream.raiseError(new Exception("wrong item encoding format"))
        }
      })
  }
}
