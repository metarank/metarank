package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.{FeaturespaceConfig, InteractionType, TrackerConfig}
import me.dfdx.metarank.tracker.state.State
import me.dfdx.metarank.tracker.{Tracker, WindowCountTracker}

case class FeatureRegistry(global: Map[InteractionType, List[Tracker[_ <: State]]]) {}

object FeatureRegistry {
  def fromConfig(conf: FeaturespaceConfig) = {
    val feedback = for {
      tpe <- conf.state
    } yield {
      tpe.interaction -> tpe.trackers.map(fromTrackerConfig)
    }
    new FeatureRegistry(feedback.toMap)
  }

  def fromTrackerConfig(conf: TrackerConfig): Tracker[_ <: State] =
    conf.name match {
      case WindowCountTracker.name => WindowCountTracker
    }
}
