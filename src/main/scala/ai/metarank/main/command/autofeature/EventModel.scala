package ai.metarank.main.command.autofeature

import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent}
import ai.metarank.model.Identifier.ItemId

case class EventModel(
    eventCount: EventCountStat = EventCountStat(),
    items: Set[ItemId] = Set.empty,
    itemFields: ItemFieldStat = ItemFieldStat(),
    interactions: InteractionStat = InteractionStat(),
    rankFields: ItemFieldStat = ItemFieldStat()
) {
  def refresh(event: Event): EventModel = {
    (event match {
      case e: ItemEvent =>
        copy(items = items + e.item, itemFields = itemFields.refresh(e))
      case e: InteractionEvent =>
        val updated = interactions.refresh(e)
        copy(interactions = updated)
      case e: RankingEvent =>
        copy(rankFields = rankFields.refresh(e))
      case _ => this
    }).copy(eventCount = eventCount.refresh(event))
  }
}
