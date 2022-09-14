package ai.metarank.main.command

import ai.metarank.config.{ApiConfig, Config, CoreConfig}
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.config.StateStoreConfig.MemoryStateConfig
import ai.metarank.main.CliArgs.AutoFeatureArgs
import ai.metarank.main.command.autofeature.{ConfigMirror, EventModel}
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.model.FeatureSchema
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO}

object AutoFeature extends Logging {
  def run(args: AutoFeatureArgs): IO[Unit] = for {
    _      <- info("Generating config file")
    source <- IO(FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format)).stream)
    model  <- source.compile.fold(EventModel())((model, event) => model.refresh(event))
    _      <- info("Event model statistics collected")
    conf   <- generateConfig(model, args.rules)
  } yield {
    val m = model
  }

  def generateConfig(model: EventModel, ruleSet: RuleSet): IO[ConfigMirror] = for {
    features <- IO(ruleSet.rules.flatMap(_.make(model)))
    featuresNel <- features match {
      case Nil          => IO.raiseError(new IllegalArgumentException("generated empty list of features"))
      case head :: tail => IO.pure(NonEmptyList(head, tail))
    }
  } yield {
    ConfigMirror(
      core = None,
      api = None,
      state = None,
      input = None,
      features = featuresNel,
      models = Map(
        "default" -> LambdaMARTConfig(
          backend = XGBoostBackend(),
          features = featuresNel.map(_.name),
          weights = model.interactions.types.map { case (interaction, count) =>
            interaction -> 1.0
          }
        )
      )
    )
  }
}
