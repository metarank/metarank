package ai.metarank.main.command.autofeature.rules

import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.model.FeatureSchema
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging

import scala.concurrent.duration._

object InteractionRateFeatureRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] = for {
    interaction <- model.interactions.types.keys.toList
  } yield {
    logger.info(s"generated rate feature over $interaction/impressions")
    RateFeatureSchema(
      name = FeatureName(s"rate_$interaction"),
      top = interaction,
      bottom = "impression",
      bucket = 1.day,
      periods = List(3, 7, 14, 30),
      scope = ItemScopeType
    )
  }

}
