package ai.metarank.model

import ai.metarank.model.Identifier.{ItemId, UserId}
import io.findify.featury.model.Key.Tenant

sealed trait FieldId {
  def tenant: Tenant
  def field: String
}

object FieldId {
  case class ItemFieldId(tenant: Tenant, item: ItemId, field: String) extends FieldId
  case class UserFieldId(tenant: Tenant, user: UserId, field: String) extends FieldId
}
