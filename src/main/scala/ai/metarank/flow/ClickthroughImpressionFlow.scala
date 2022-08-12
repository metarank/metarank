package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{Clickthrough, Event, EventId, ItemValue}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Stream}

case class ClickthroughImpressionFlow(state: Persistence, mapping: FeatureMapping) extends Logging {
  def process: Pipe[IO, Event, Event] = events =>
    events
      .evalMap(event => {
        val br = 1
        event match {
          case event: RankingEvent     => handleRanking(event).map(e => List(e))
          case event: InteractionEvent => handleInteraction(event)
          case other                   => IO.pure(List(other))

        }
      })
      .flatMap(list => Stream.emits(list))
      .chunkN(1024)
      .flatMap(c => Stream.emits(c.toList))

  def handleRanking(event: RankingEvent): IO[Event] = {
    val br = 1
    for {
      keys    <- IO(mapping.features.flatMap(_.valueKeys(event)))
      values  <- state.values.get(keys)
      mvalues <- IO(ItemValue.fromState(event, values, mapping))
      _       <- state.cts.putRanking(event)
      _       <- state.cts.putValues(event.id, mvalues)
    } yield {
      event
    }
  }

  def handleInteraction(event: InteractionEvent): IO[List[Event]] = {
    event.ranking match {
      case None => IO.pure(Nil)
      case Some(id) =>
        state.cts.getClickthrough(id).flatMap {
          case None => IO.pure(List(event))
          case Some(ct) =>
            val syntheticImpressions = impressions(ct, event).map(item =>
              InteractionEvent(
                id = event.id,
                item = item,
                timestamp = event.timestamp,
                ranking = Some(id),
                user = event.user,
                session = event.session,
                `type` = "impression"
              )
            )
            if (syntheticImpressions.nonEmpty) logger.debug(s"generated ${syntheticImpressions.size} impressions")
            state.cts.putInteraction(id, event.item, event.`type`).map(_ => event +: syntheticImpressions)
        }
    }
  }

  def impressions(ct: Clickthrough, int: InteractionEvent): List[ItemId] = {
    val positions    = ct.items.zipWithIndex.toMap
    val lastPosition = ct.interactions.flatMap(e => positions.get(e.item)).maxOption
    val intPosition  = positions.get(int.item)
    (lastPosition, intPosition) match {
      case (Some(last), Some(current)) if current > last => ct.items.slice(last + 1, current + 1)
      case (None, Some(current))                         => ct.items.take(current + 1)
      case _                                             => Nil
    }
  }

}
