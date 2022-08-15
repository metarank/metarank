package ai.metarank.flow

import ai.metarank.model.{Event, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2._

object OrderCheckFlow extends Logging {
  def process: Pipe[IO, Event, Event] = events => pull(events, Timestamp(0)).stream

  def pull(s: fs2.Stream[IO, Event], ts: Timestamp): Pull[IO, Event, Unit] = s.pull.uncons.flatMap {
    case Some((head, tail)) =>
      isSorted(head.toList, ts) match {
        case Some(next) => Pull.output(head) >> pull(tail, next)
        case _          => Pull.raiseError[IO](new Exception("input not sorted"))
      }
    case None => Pull.done
  }

  def isSorted(events: List[Event], first: Timestamp): Option[Timestamp] = {
    events.foldLeft(Option(first))((acc, next) =>
      acc match {
        case Some(ts) if next.timestamp.isAfterOrEquals(ts) => Some(next.timestamp)
        case Some(ts) =>
          logger.warn(s"event ${next.id.value} has ts=${next.timestamp}, which is earlier than previous ts=$ts")
          None
        case _ => None
      }
    )
  }
}
