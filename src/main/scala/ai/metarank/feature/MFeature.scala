package ai.metarank.feature

import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, MetadataEvent, RankingEvent}
import ai.metarank.model.FeatureScope.{GlobalScope, ItemScope, SessionScope, UserScope}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldSchema, MValue}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.{Feature, FeatureConfig, FeatureValue, Key, State, Write}

trait MFeature {
  def dim: Int
  def fields: List[FieldSchema]
  def schema: FeatureSchema
  def states: List[FeatureConfig]
  def writes(event: Event): Traversable[Write]
  def keys(request: RankingEvent): Traversable[Key]
  def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue

  // tenant from event
  protected def tenant(event: Event): Tenant = Tenant(event.tenant.getOrElse("default"))

  def keyOf(event: Event): Option[Key] = (schema.scope, event) match {
    case (GlobalScope, _)                 => Some(keyOf(GlobalScope.value, "global", schema.name, event.tenant))
    case (UserScope, e: FeedbackEvent)    => Some(keyOf(UserScope.value, e.user.value, schema.name, event.tenant))
    case (SessionScope, e: FeedbackEvent) => Some(keyOf(SessionScope.value, e.session.value, schema.name, event.tenant))
    case (ItemScope, e: InteractionEvent) => Some(keyOf(ItemScope.value, e.item.value, schema.name, event.tenant))
    case (ItemScope, e: MetadataEvent)    => Some(keyOf(ItemScope.value, e.item.value, schema.name, event.tenant))
    case _                                => None
  }

  private def keyOf(scope: String, id: String, name: String, t: Option[String]): Key = {
    Key(Tag(Scope(scope), id), FeatureName(name), Tenant(t.getOrElse("default")))
  }

}
