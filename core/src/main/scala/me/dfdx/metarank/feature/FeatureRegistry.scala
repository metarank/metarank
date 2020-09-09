package me.dfdx.metarank.feature

import me.dfdx.metarank.config.Config.{FeatureConfig, FeedbackConfig}

case class FeatureRegistry(feedback: Map[String, List[Feature]]) {}

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
      case TumblingWindowCountingFeature.name => TumblingWindowCountingFeature(conf.windows, conf.days, interactionType)
    }
}
