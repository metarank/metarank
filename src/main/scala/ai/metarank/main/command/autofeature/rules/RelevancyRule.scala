package ai.metarank.main.command.autofeature.rules
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.main.command.autofeature.{EventModel, RelevancyStat}
import ai.metarank.model.FeatureSchema
import ai.metarank.model.Key.FeatureName
import ai.metarank.util.Logging

object RelevancyRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] = model.relevancy match {
    case RelevancyStat(nonZero, Some(min), Some(max)) if (nonZero > 0) && (max > min) =>
      logger.info(s"generated relevance feature: non_zero=${nonZero} min=$min max=$max")
      List(RelevancySchema(FeatureName("relevancy")))
    case RelevancyStat(nonZero, min, max) =>
      logger.info(s"skipped generating relevancy feature: non_zero=${nonZero} min=$min max=$max")
      Nil
  }
}
