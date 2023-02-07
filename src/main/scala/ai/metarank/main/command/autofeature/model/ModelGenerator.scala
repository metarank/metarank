package ai.metarank.main.command.autofeature.model

import ai.metarank.config.ModelConfig
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.main.command.autofeature.model.ModelGenerator.ModelConfigMirror
import ai.metarank.model.FeatureSchema

trait ModelGenerator {
  def maybeGenerate(events: EventModel, features: List[FeatureSchema]): Option[ModelConfigMirror]
}

object ModelGenerator {
  case class ModelConfigMirror(name: String, conf: ModelConfig)
}
