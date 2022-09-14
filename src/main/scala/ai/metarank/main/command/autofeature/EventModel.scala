package ai.metarank.main.command.autofeature

import ai.metarank.model.Event
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent}
import ai.metarank.model.Identifier.ItemId

case class EventModel(
    items: Set[ItemId] = Set.empty,
    itemFields: ItemFieldStat = ItemFieldStat(),
    interactions: InteractionStat = InteractionStat(),
    relevancy: RelevancyStat = RelevancyStat()
) {
  def refresh(event: Event): EventModel = {
    event match {
      case e: ItemEvent =>
        copy(items = items + e.item, itemFields = itemFields.refresh(e))
      case e: InteractionEvent =>
        val updated = interactions.refresh(e)
        copy(interactions = updated)
      case e: RankingEvent =>
        copy(relevancy = relevancy.refresh(e))
      case _ => this
    }
  }
}
