package ai.metarank.source2

import ai.metarank.config.InputConfig
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.model.Event
import cats.effect.IO

trait EventSource2 {
  def stream: fs2.Stream[IO, Event]
}

object EventSource2 {}
