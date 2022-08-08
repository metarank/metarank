package ai.metarank.model

import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, RankingEvent}
import ai.metarank.model.Identifier.ItemId
import io.circe.Codec
import io.circe.generic.semiauto._

case class Clickthrough(
    ranking: RankingEvent,
    interactions: List[InteractionEvent] = Nil,
    values: List[ItemValue] = Nil
) {
  def withInteraction(int: InteractionEvent): Clickthrough =
    copy(interactions = int +: interactions)

  def impressions(int: InteractionEvent): List[ItemId] = {
    val positions    = ranking.items.toList.map(_.id).zipWithIndex.toMap
    val lastPosition = interactions.flatMap(e => positions.get(e.item)).maxOption
    val intPosition  = positions.get(int.item)
    (lastPosition, intPosition) match {
      case (Some(last), Some(current)) if current > last => ranking.items.toList.slice(last + 1, current + 1).map(_.id)
      case (None, Some(current))                         => ranking.items.toList.take(current + 1).map(_.id)
      case _                                             => Nil
    }
  }
}

object Clickthrough {
  import ai.metarank.model.Event.EventCodecs._
  implicit val ctCodec: Codec[Clickthrough] = deriveCodec[Clickthrough]
}
