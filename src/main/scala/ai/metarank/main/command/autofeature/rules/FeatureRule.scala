package ai.metarank.main.command.autofeature.rules

import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.model.FeatureSchema

trait FeatureRule {
  def make(model: EventModel): List[FeatureSchema]
}

object FeatureRule {}
