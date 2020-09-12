package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.{FeaturespaceConfig, EventType}
import me.dfdx.metarank.tracker.state.State
import me.dfdx.metarank.tracker.{Aggregation, WindowCountAggregation}

case class FeatureRegistry(global: Map[EventType, List[Aggregation]]) {}

object FeatureRegistry {
//  def fromConfig(conf: FeaturespaceConfig) = {
//    val feedback = for {
//      event <- conf.events
//    } yield {
//      event.`type` -> event.aggregations.map(fromAggregationConfig)
//    }
//    new FeatureRegistry(feedback.toMap)
//  }
//
//  def fromAggregationConfig(conf: AggregationConfig): Aggregation[_ <: State] =
//    conf.name match {
//      case WindowCountAggregation.name => WindowCountAggregation
//    }
}
