package ai.metarank.feature

import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, MetadataEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldSchema, ItemId, MValue}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, Write}

trait MFeature {
  def dim: Int
  def fields: List[FieldSchema]
  def schema: FeatureSchema
  def states: List[FeatureConfig]
  def writes(event: Event): Traversable[Write]
  def prekeys(request: RankingEvent): Traversable[Key] = Nil
  def keys(request: RankingEvent, prestate: Map[Key, FeatureValue]): Traversable[Key]
  def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemId
  ): MValue

  def keyOf(event: Event): Option[Key] = (schema.scope, event) match {
    case (TenantScope, _)                 => Some(keyOf(TenantScope.value, event.tenant, schema.name, event.tenant))
    case (UserScope, e: FeedbackEvent)    => Some(keyOf(UserScope.value, e.user.value, schema.name, event.tenant))
    case (SessionScope, e: FeedbackEvent) => Some(keyOf(SessionScope.value, e.session.value, schema.name, event.tenant))
    case (ItemScope, e: InteractionEvent) => Some(keyOf(ItemScope.value, e.item.value, schema.name, event.tenant))
    case (ItemScope, e: MetadataEvent)    => Some(keyOf(ItemScope.value, e.item.value, schema.name, event.tenant))
    case _                                => None
  }

  def keyOf(scope: FeatureScope, id: ItemId, name: FeatureName, t: String): Key = {
    Key(Tag(Scope(scope.value), id.value), name, Tenant(t))
  }

  def keyOf(scope: String, id: String, name: String, t: String): Key = {
    Key(Tag(Scope(scope), id), FeatureName(name), Tenant(t))
  }

}
