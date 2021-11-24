package ai.metarank.feature

import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{Event, FeatureSchema, MValue}
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{Feature, FeatureConfig, FeatureValue, Key, State, Write}

trait MFeature {
  def dim: Int
  def schema: FeatureSchema
  def states: List[FeatureConfig]
  def writes(event: Event): Traversable[Write]
  def keys(request: RankingEvent): Traversable[Key]
  def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue

  // tenant from event
  protected def tenant(event: Event): Tenant = Tenant(event.tenant.getOrElse("default"))
}
