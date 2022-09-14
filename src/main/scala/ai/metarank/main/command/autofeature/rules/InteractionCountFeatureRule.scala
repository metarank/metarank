package ai.metarank.main.command.autofeature.rules

import ai.metarank.feature.WindowInteractionCountFeature.WindowInteractionCountSchema
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.model.FeatureSchema
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging

import scala.concurrent.duration._

object InteractionCountFeatureRule extends FeatureRule with Logging {
  override def make(model: EventModel): List[FeatureSchema] = for {
    interaction <- model.interactions.types.keys.toList
  } yield {
    logger.info(s"generated window_count feature over interaction '$interaction'")
    WindowInteractionCountSchema(
      name = FeatureName(s"count_$interaction"),
      interaction = interaction,
      bucket = 1.day,
      periods = List(3, 7, 14, 30),
      scope = ItemScopeType
    )
  }
}
