package ai.metarank.feature

import ai.metarank.model.Event.ImpressionEvent
import ai.metarank.model.{Event, FeatureSchema, MValue}
import io.findify.featury.model.{Feature, FeatureConfig, FeatureValue, Key, State, Write}

trait MFeature[S <: FeatureSchema] {
  def dim: Int
  def schema: S
  def states: List[FeatureConfig]
  def writes(event: Event): Traversable[Write]
  def keys(request: ImpressionEvent): Traversable[Key]
  def value(request: Event.ImpressionEvent, state: Map[Key, FeatureValue], id: String): MValue
}
