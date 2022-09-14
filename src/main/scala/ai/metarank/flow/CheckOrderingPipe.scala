package ai.metarank.flow

import ai.metarank.model.{Event, Timestamp}
import cats.effect.IO
import fs2.{Pipe, Pull, Stream}

object CheckOrderingPipe {
  def process: Pipe[IO, Event, Event] = stream => next(stream, None).stream

  def next(stream: Stream[IO, Event], prevOpt: Option[Event]): Pull[IO, Event, Unit] =
    stream.pull.uncons1.flatMap {
      case Some((head, tail)) =>
        prevOpt match {
          case Some(prev) if head.timestamp.isBefore(prev.timestamp) =>
            Pull.raiseError[IO](
              new Exception(
                s"""Events are not sorted:
                   |  current event: id=${head.id.value} ts=${head.timestamp.toString}
                   |  previous event: id=${prev.id.value} ts=${prev.timestamp.toString}
                   |  current event happened ${prev.timestamp.diff(head.timestamp)} before previous.
                   |  
                   |Metarank expects that events should be sorted by timestamp, as it replays your click-through history.
                   |Please pre-sort the events: metarank sort --config conf.yml --data path/to/events --out path/out\n\n""".stripMargin
              )
            )
          case _ =>
            Pull.output1(head) >> next(tail, Some(head))
        }
      case None => Pull.done
    }
}
