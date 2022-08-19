package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event
import cats.effect.IO
import fs2.Stream

object MetarankFlow {
  def process(store: Persistence, source: Stream[IO, Event], mapping: FeatureMapping): IO[Unit] = {
    val ct    = ClickthroughImpressionFlow(store, mapping)
    val event = FeatureValueFlow(mapping, store)
    val sink  = FeatureValueSink(store)
    source
      .through(ai.metarank.flow.PrintProgress.tap)
      .through(ct.process)
      .through(event.process)
      .through(sink.write)
      .compile
      .drain
  }
}
