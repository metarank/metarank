package ai.metarank.model

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Event.{InteractionEvent, ItemRelevancy, RankingEvent}
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import io.circe.Codec
import io.circe.generic.semiauto._

case class Clickthrough(
    id: EventId,
    ts: Timestamp,
    user: UserId,
    session: Option[SessionId],
    items: List[ItemId],
    interactions: List[TypedInteraction] = Nil
) {
  def withInteraction(item: ItemId, tpe: String): Clickthrough =
    copy(interactions = TypedInteraction(item, tpe) +: interactions)

}

object Clickthrough {
  case class TypedInteraction(item: ItemId, tpe: String)
  import ai.metarank.model.Event.EventCodecs._
  implicit val wiCodec: Codec[TypedInteraction] = deriveCodec[TypedInteraction]
  implicit val ctCodec: Codec[Clickthrough]     = deriveCodec[Clickthrough]
}
