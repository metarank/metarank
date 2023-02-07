package ai.metarank.main.command.autofeature

import ai.metarank.config.BoosterConfig.XGBoostConfig
import ai.metarank.config._
import ai.metarank.main.command.autofeature.model.{
  LambdaMARTConfigGenerator,
  ModelGenerator,
  SimilarRecsConfigGenerator,
  TrendingRecsConfigGenerator
}
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.model.FeatureSchema
import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ConfigMirror(
    features: List[FeatureSchema],
    models: Map[String, ModelConfig]
)
object ConfigMirror {
  implicit val configMirrorEncoder: Encoder[ConfigMirror] = deriveEncoder[ConfigMirror]

  val generators: List[ModelGenerator] = List(
    LambdaMARTConfigGenerator,
    SimilarRecsConfigGenerator,
    TrendingRecsConfigGenerator
  )

  def create(model: EventModel, ruleSet: RuleSet): IO[ConfigMirror] = for {
    features <- IO(ruleSet.rules.flatMap(_.make(model)).sortBy(_.name.value))
    models <- generators.flatMap(_.maybeGenerate(model, features)) match {
      case Nil =>
        IO.raiseError(
          new Exception(
            s"""Cannot generate at least one model based on provided data. This may happen due to these reasons:
               |1. Not enough data. ML model training requires a proper underlying dataset with multiple data samples.
               |   Maybe your dataset is too small?
               |2. There's a bug in Metarank and it's being too strict to your data. If you think that's the case, you
               |   can report an issue on Github, but please describe the dataset you've used for it.
               |""".stripMargin
          )
        )
      case nel => IO(nel.map(mc => mc.name -> mc.conf).toMap)
    }
  } yield {
    ConfigMirror(
      features = features,
      models = models
    )
  }
}
