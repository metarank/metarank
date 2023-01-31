package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.SessionScope
import ai.metarank.model.{Clickthrough, Event, EventId, ItemValue, Key}
import ai.metarank.util.Logging
import cats.effect.IO
import fs2.{Pipe, Stream}

object ImpressionInject extends Logging {

  def process(ct: Clickthrough): List[InteractionEvent] = {
    val positions = ct.items.zipWithIndex.toMap
    ct.interactions.flatMap(i => positions.get(i.item)).maxOption match {
      case None =>
        logger.warn(
          s"received interactions ${ct.interactions} over non-existent items ${ct.items}, parent ranking=${ct.id} user=${ct.user}"
        )
        Nil
      case Some(maxPosition) =>
        ct.items
          .take(maxPosition + 1)
          .map(item =>
            InteractionEvent(
              id = ct.id,
              item = item,
              timestamp = ct.ts,
              ranking = Some(ct.id),
              user = ct.user,
              session = ct.session,
              `type` = "impression"
            )
          )

    }

  }
}
