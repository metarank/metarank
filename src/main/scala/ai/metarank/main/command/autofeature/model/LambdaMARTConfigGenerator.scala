package ai.metarank.main.command.autofeature.model
import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.main.command.autofeature.EventModel
import ai.metarank.main.command.autofeature.model.ModelGenerator.ModelConfigMirror
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.model.FeatureSchema
import ai.metarank.util.Logging
import cats.data.NonEmptyList

object LambdaMARTConfigGenerator extends ModelGenerator with Logging {
  override def maybeGenerate(
      events: EventModel,
      features: List[FeatureSchema]
  ): Option[ModelGenerator.ModelConfigMirror] = {
    if (events.eventCount.rankings < 100) {
      logger.warn(
        s"""cannot generate LambdaMART model config: found ${events.eventCount.rankings} ranking events, and it
           |should be more for a successful ML model training. A reasonable minimal amount of rankings is 100.
           |""".stripMargin
      )
      None
    } else if (events.eventCount.intsWithRanking < 100) {
      logger.warn(
        s"""cannot generate LambdaMART model config: there's only ${events.eventCount.intsWithRanking} click-throughs,
           |and it should be more for a successful ML model training. A reasonable minimal amount of
           |click-through events is 100.
           |""".stripMargin
      )
      None
    } else {
      features match {
        case Nil =>
          logger.info(s"cannot generate lmart model: cannot generate at least one feature definitions")
          None
        case head :: tail =>
          val featuresNel = NonEmptyList.of(head, tail: _*)
          Some(
            ModelConfigMirror(
              "default",
              LambdaMARTConfig(
                backend = XGBoostConfig(
                  iterations = 50,
                  seed = 0
                ),
                features = featuresNel.map(_.name).sortBy(_.value),
                weights = events.interactions.types.map { case (interaction, _) => interaction -> 1.0 }
              )
            )
          )
      }
    }
  }
}
