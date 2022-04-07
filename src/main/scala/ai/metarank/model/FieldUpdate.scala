package ai.metarank.model

import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankingEvent, UserEvent}
import ai.metarank.model.FieldId.{ItemFieldId, UserFieldId}
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.Identifier.{ItemId, UserId}
import io.findify.featury.model.Key.Tenant

case class FieldUpdate(id: FieldId, value: Field)

object FieldUpdate {

  def fromEvent(event: Event): List[FieldUpdate] = event match {
    case event: ItemEvent =>
      event.fields.map(f => FieldUpdate(ItemFieldId(Tenant(event.tenant), event.item, f.name), f))
    case event: UserEvent =>
      event.fields.map(f => FieldUpdate(UserFieldId(Tenant(event.tenant), event.user, f.name), f))
    case _ => Nil
  }
}
