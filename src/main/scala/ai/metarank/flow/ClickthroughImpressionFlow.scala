package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, Env, Event, EventId, ItemValue}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Stream}

case class ClickthroughImpressionFlow(state: Persistence, mappings: Map[Env, FeatureMapping]) extends Logging {
  def process: Pipe[IO, Event, Event] = events =>
    events.flatMap {
      case event: RankingEvent =>
        mappings.get(event.env) match {
          case Some(mapping) => Stream.eval(handleRanking(event, mapping))
          case None =>
            Stream.raiseError[IO](
              new Exception(s"received event for env ${event.env.value}, which is undefined in config")
            )
        }
      case event: InteractionEvent => Stream.evalSeq(handleInteraction(event))
      case other                   => Stream.emit(other)
    }

  def handleRanking(event: RankingEvent, mapping: FeatureMapping): IO[Event] = {
    for {
      keys    <- IO(mapping.features.flatMap(_.valueKeys(event)))
      values  <- state.values.get(keys)
      mvalues <- IO(ItemValue.fromState(event, values, mapping))
      _       <- state.cts.put(Clickthrough(event, values = mvalues))
    } yield {
      event
    }
  }

  def handleInteraction(event: InteractionEvent): IO[List[Event]] = {
    event.ranking match {
      case None => IO.pure(Nil)
      case Some(id) =>
        state.cts.get(id).flatMap {
          case Some(ct) =>
            state.cts
              .put(ct.withInteraction(event))
              .map(_ => {
                val syntheticImpressions = ct
                  .impressions(event)
                  .map(item =>
                    InteractionEvent(
                      id = event.id,
                      item = item,
                      timestamp = event.timestamp,
                      ranking = Some(ct.ranking.id),
                      user = event.user,
                      session = event.session,
                      `type` = "impression",
                      env = event.env
                    )
                  )
                if (syntheticImpressions.nonEmpty) logger.debug(s"generated ${syntheticImpressions.size} impressions")
                event +: syntheticImpressions
              })
          case None =>
            IO.pure(List(event))
        }
    }
  }
}
