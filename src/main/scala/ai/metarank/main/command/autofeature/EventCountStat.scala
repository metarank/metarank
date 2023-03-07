package ai.metarank.main.command.autofeature

import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}

case class EventCountStat(items: Int = 0, users: Int = 0, rankings: Int = 0, ints: Int = 0, intsWithRanking: Int = 0) {
  def total = items + users + rankings + ints
  def refresh(event: Event): EventCountStat = event match {
    case e: ItemEvent    => copy(items = items + 1)
    case e: UserEvent    => copy(users = users + 1)
    case e: RankingEvent => copy(rankings = rankings + 1)
    case e: InteractionEvent =>
      copy(ints = ints + 1, intsWithRanking = intsWithRanking + (if (e.ranking.isDefined) 1 else 0))
  }
}
