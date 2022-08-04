package ai.metarank.model

import ai.metarank.model.Clickthrough.ItemValues
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.Identifier.ItemId

case class Clickthrough(
    ranking: RankingEvent,
    interactions: List[InteractionEvent],
    features: List[FeatureValue] = Nil,
    values: List[ItemValues] = Nil
)

object Clickthrough {
  case class ItemValues(id: ItemId, label: Double, values: List[MValue], score: Double = 0)
}
