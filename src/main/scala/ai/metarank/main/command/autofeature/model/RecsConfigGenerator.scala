package ai.metarank.main.command.autofeature.model

import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.main.command.autofeature.model.ModelGenerator.ModelConfigMirror
import ai.metarank.main.command.autofeature.model.SimilarRecsConfigGenerator.logger
import ai.metarank.ml.recommend.TrendingRecommender.{InteractionWeight, TrendingConfig}
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging

trait RecsConfigGenerator extends ModelGenerator with Logging {
  def name: String
  def makeConfig(ints: List[String]): ModelConfigMirror

  override def maybeGenerate(
      events: EventModel,
      features: List[FeatureSchema]
  ): Option[ModelGenerator.ModelConfigMirror] = {
    if (events.eventCount.ints < 10) {
      logger.warn(
        s"""cannot generate $name model config: there's only ${events.eventCount.ints} interactions,
           |and it should be more. A reasonable minimal amount of click-through events is 10.
           |""".stripMargin
      )
      None
    } else if (events.interactions.types.isEmpty) {
      logger.warn(
        s"""cannot generate $name model config: could not find at least one interaction type in the data,
           |and it should be at least one. Check your data (and interaction events) for the "type" field.
           |""".stripMargin
      )
      None
    } else {
      Some(makeConfig(events.interactions.types.keys.toList))
    }
  }

}
