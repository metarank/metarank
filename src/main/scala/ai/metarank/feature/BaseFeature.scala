package ai.metarank.feature

import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, ItemRelevancy, MetadataEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldSchema, ItemId, MValue}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, Write}

sealed trait BaseFeature {
  def dim: Int
  def fields: List[FieldSchema]
  def schema: FeatureSchema
  def states: List[FeatureConfig]
  def writes(event: Event): Traversable[Write]

  def keyOf(event: Event, item: Option[ItemId] = None): Option[Key] = (schema.scope, event) match {
    case (TenantScope, _) => Some(keyOf(TenantScope.scope.name, event.tenant, schema.name, event.tenant))
    case (UserScope, e: FeedbackEvent) =>
      Some(keyOf(UserScope.scope.name, e.user.value, schema.name, event.tenant))
    case (SessionScope, e: FeedbackEvent) =>
      Some(keyOf(SessionScope.scope.name, e.session.value, schema.name, event.tenant))
    case (ItemScope, e: InteractionEvent) => Some(keyOf(ItemScope.scope.name, e.item.value, schema.name, e.tenant))
    case (ItemScope, e: MetadataEvent)    => Some(keyOf(ItemScope.scope.name, e.item.value, schema.name, e.tenant))
    case (ItemScope, e: RankingEvent)     => item.map(i => keyOf(ItemScope.scope.name, i.value, schema.name, e.tenant))
    case _                                => None
  }

  def keyOf(scope: FeatureScope, id: ItemId, name: FeatureName, t: String): Key = {
    Key(Tag(scope.scope, id.value), name, Tenant(t))
  }

  def keyOf(scope: String, id: String, name: String, t: String): Key = {
    Key(Tag(Scope(scope), id), FeatureName(name), Tenant(t))
  }

}

object BaseFeature {

  sealed trait ItemFeature extends BaseFeature {
    def value(
        request: Event.RankingEvent,
        state: Map[Key, FeatureValue],
        id: ItemRelevancy
    ): MValue
  }

  trait RankingStatelessFeature extends BaseFeature {
    def value(
        request: Event.RankingEvent,
        state: Map[Key, FeatureValue]
    ): MValue
  }

  trait ItemStatelessFeature extends ItemFeature {}

  trait StatefulFeature extends ItemFeature {
    def writes(event: Event, state: Map[Key, FeatureValue]): Traversable[Write]
  }
}
