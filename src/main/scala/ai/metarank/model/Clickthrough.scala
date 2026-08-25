package ai.metarank.model

import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import io.circe.Codec
import io.circe.generic.semiauto.*

case class Clickthrough(
    id: EventId,
    ts: Timestamp,
    user: Option[UserId],
    session: Option[SessionId],
    items: List[ItemId],
    interactions: List[TypedInteraction] = Nil,
    rankingFields: List[Field] = Nil
) {
  def withInteraction(item: ItemId, tpe: String): Clickthrough =
    copy(interactions = TypedInteraction(item, tpe) +: interactions)

}

object Clickthrough {
  case class TypedInteraction(item: ItemId, tpe: String, rel: Option[Int] = None)
  import ai.metarank.model.Event.EventCodecs.given
  given wiCodec: Codec[TypedInteraction] = deriveCodec[TypedInteraction]
  given ctCodec: Codec[Clickthrough]     = deriveCodec[Clickthrough]
}
