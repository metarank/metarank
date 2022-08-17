package ai.metarank.source

import ai.metarank.model.{Event, Field}
import cats.effect.IO
import cats.effect.std.Queue
import fs2.Stream

case class RestApiEventSource(queue: Queue[IO, Option[Event]]) extends EventSource {
  override def stream: fs2.Stream[IO, Event] = Stream.fromQueueNoneTerminated(queue)
}
