package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, CoreConfig}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event
import cats.effect.{IO, Ref}
import fs2.Stream

object MetarankFlow {
  case class ProcessResult(events: Long, updates: Long, tookMillis: Long)
  def process(
      store: Persistence,
      source: Stream[IO, Event],
      mapping: FeatureMapping,
      clickthrough: ClickthroughJoinBuffer
  ): IO[ProcessResult] = {
    val event = FeatureValueFlow(mapping, store)
    val sink  = FeatureValueSink(store)

    for {
      start         <- IO(System.currentTimeMillis())
      eventCounter  <- Ref.of[IO, Long](0)
      updateCounter <- Ref.of[IO, Long](0)
      _ <- source
        .evalTapChunk(e => IO(store.ticker.tick(e)))
        .evalTapChunk(_ => eventCounter.update(_ + 1))
        .through(ai.metarank.flow.PrintProgress.tap)
        .flatMap(event =>
          Stream.evalSeq[IO, List, Event](
            clickthrough
              .process(event)
              .map(cts => event +: cts.flatMap(ct => ImpressionInject.process(ct)))
          )
        )
        .through(event.process)
        .evalTapChunk(values => updateCounter.update(_ + values.size))
        .through(sink.write)
        .compile
        .drain
      events  <- eventCounter.get
      updates <- updateCounter.get
      end     <- IO(System.currentTimeMillis())
    } yield {
      ProcessResult(events, updates, end - start)
    }
  }
}
