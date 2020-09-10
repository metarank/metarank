package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.{FeatureConfig, FeedbackConfig, InteractionType}
import me.dfdx.metarank.state.State

case class FeatureRegistry(global: Map[InteractionType, List[Feature[_ <: State]]]) {}

object FeatureRegistry {
  def fromConfig(feedbackConfig: FeedbackConfig) = {
    val feedback = for {
      tpe <- feedbackConfig.types
    } yield {
      tpe.name -> tpe.features.map(fromFeatureConfig(tpe.name, _))
    }
    new FeatureRegistry(feedback.toMap)
  }

  def fromFeatureConfig(interactionType: InteractionType, conf: FeatureConfig): Feature[_ <: State] =
    conf.name match {
      case "window_count" => WindowCountingFeature(conf.windows, interactionType)
    }
}
