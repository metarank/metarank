package ai.metarank.main.command.autofeature

import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.config.StateStoreConfig.MemoryStateConfig
import ai.metarank.config._
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.model.FeatureSchema
import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ConfigMirror(
    features: NonEmptyList[FeatureSchema],
    models: Map[String, ModelConfig]
)
object ConfigMirror {
  implicit val configMirrorEncoder: Encoder[ConfigMirror] = deriveEncoder[ConfigMirror]

  def create(model: EventModel, ruleSet: RuleSet): IO[ConfigMirror] = for {
    features <- IO(ruleSet.rules.flatMap(_.make(model)))
    featuresNel <- features match {
      case Nil          => IO.raiseError(new IllegalArgumentException("generated empty list of features"))
      case head :: tail => IO.pure(NonEmptyList(head, tail))
    }
  } yield {
    ConfigMirror(
      features = featuresNel,
      models = Map(
        "default" -> LambdaMARTConfig(
          backend = XGBoostBackend(),
          features = featuresNel.map(_.name),
          weights = model.interactions.types.map { case (interaction, _) => interaction -> 1.0 }
        )
      )
    )
  }
}
