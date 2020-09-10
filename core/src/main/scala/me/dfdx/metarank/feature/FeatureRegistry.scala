package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.{FeatureConfig, FeedbackConfig}
import me.dfdx.metarank.state.State

case class FeatureRegistry(global: Map[String, List[Feature[_ <: State]]]) {}

object FeatureRegistry {
  def fromConfig(feedbackConfig: FeedbackConfig) = {
    val feedback = for {
      tpe <- feedbackConfig.types
    } yield {
      tpe.name -> tpe.features.map(fromFeatureConfig(tpe.name, _))
    }
    new FeatureRegistry(feedback.toMap)
  }

  def fromFeatureConfig(interactionType: String, conf: FeatureConfig) =
    conf.name match {
      case "tumbling_count" => TumblingWindowCountingFeature(conf.windows, interactionType)
    }
}
