package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.{Event, TrainValues}
import ai.metarank.util.analytics.Metrics
import cats.effect.{IO, Ref}
import fs2.Stream

object MetarankFlow {
  case class ProcessResult(events: Long, updates: Long, tookMillis: Long)
  def process(
      store: Persistence,
      source: Stream[IO, Event],
      mapping: FeatureMapping,
      clickthrough: TrainBuffer,
      flushOnComplete: Boolean = true
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
        .evalTapChunk(e => IO(Metrics.events.inc()))
        .through(ai.metarank.flow.PrintProgress.tap(Some(store), "events"))
        .flatMap(event =>
          Stream.evalSeq[IO, List, Event](
            clickthrough.process(event).map(cts => event +: injectImpressions(cts))
          )
        )
        .onComplete(
          if (flushOnComplete) Stream.evalSeq[IO, List, Event](clickthrough.flushAll().map(injectImpressions))
          else Stream.empty
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

  // finalizes all buffered clickthroughs and runs the synthetic impressions through the feature pipeline
  def flush(store: Persistence, mapping: FeatureMapping, clickthrough: TrainBuffer): IO[ProcessResult] =
    process(store, Stream.empty, mapping, clickthrough)

  private def injectImpressions(cts: List[TrainValues]): List[Event] = cts.flatMap {
    case TrainValues.ClickthroughValues(ct, _) => ImpressionInject.process(ct)
    case _                                     => Nil
  }
}
