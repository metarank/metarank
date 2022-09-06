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
    val positions   = ct.items.zipWithIndex.toMap
    val maxPosition = ct.interactions.flatMap(i => positions.get(i.item)).max
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
