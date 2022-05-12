package ai.metarank.feature

import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, ItemEvent, ItemRelevancy, RankingEvent, UserEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.Identifier._
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, Write}

sealed trait BaseFeature {
  def dim: Int
  def fields: List[FieldName]
  def schema: FeatureSchema
  def states: List[FeatureConfig]
  def writes(event: Event, fields: FieldStore): Iterable[Write]

  def keyOf(event: Event, item: Option[ItemId] = None): Option[Key] = (schema.scope, event) match {
    case (TenantScope, _) => Some(keyOf(TenantScope.scope.name, event.tenant, schema.name, event.tenant))
    case (UserScope, e: FeedbackEvent) =>
      e.user.map(user => keyOf(UserScope.scope.name, user.value, schema.name, event.tenant))
    case (SessionScope, e: FeedbackEvent) =>
      e.session.map(session => keyOf(SessionScope.scope.name, session.value, schema.name, event.tenant))
    case (ItemScope, e: InteractionEvent) => Some(keyOf(ItemScope.scope.name, e.item.value, schema.name, e.tenant))
    case (ItemScope, e: ItemEvent)        => Some(keyOf(ItemScope.scope.name, e.item.value, schema.name, e.tenant))
    case (ItemScope, e: RankingEvent)     => item.map(i => keyOf(ItemScope.scope.name, i.value, schema.name, e.tenant))
    case (UserScope, e: UserEvent)        => Some(keyOf(UserScope.scope.name, e.user.value, schema.name, event.tenant))
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

  trait ItemFeature extends BaseFeature {
    def value(
        request: Event.RankingEvent,
        features: Map[Key, FeatureValue],
        id: ItemRelevancy
    ): MValue

    def values(request: Event.RankingEvent, features: Map[Key, FeatureValue]): List[MValue] =
      request.items.toList.map(item => value(request, features, item))
  }

  trait RankingFeature extends BaseFeature {
    def value(
        request: Event.RankingEvent,
        features: Map[Key, FeatureValue]
    ): MValue
  }

}
