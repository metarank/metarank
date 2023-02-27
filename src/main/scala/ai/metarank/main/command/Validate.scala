package ai.metarank.main.command

import ai.metarank.config.Config
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.flow.PrintProgress
import ai.metarank.main.CliArgs.ValidateArgs
import ai.metarank.model.Event
import ai.metarank.source.FileEventSource
import ai.metarank.util.Logging
import ai.metarank.validate.checks.StringValuesValidation.{ItemStringValuesValidation, UserStringValuesValidation}
import ai.metarank.validate.checks.{
  EventOrderValidation,
  EventTypesValidation,
  FeatureOverMissingFieldValidation,
  InteractionKeyValidation,
  InteractionMetadataValidation,
  InteractionPositionValidation,
  InteractionTypeValidation
}
import cats.effect.IO
import io.circe.syntax._

import java.io.FileOutputStream

object Validate extends Logging {
  def run(conf: Config, args: ValidateArgs): IO[Unit] = for {
    _ <- validate(
      conf,
      FileEventSource(FileInputConfig(args.data.toString, args.offset, args.format, args.sort)).stream
        .through(PrintProgress.tap(None, "events"))
    )
  } yield {}

  def validate(conf: Config, eventStream: fs2.Stream[IO, Event]) = {
    val validators = List(
      EventOrderValidation,
      EventTypesValidation,
      FeatureOverMissingFieldValidation,
      InteractionKeyValidation,
      InteractionMetadataValidation,
      InteractionPositionValidation,
      InteractionTypeValidation,
      UserStringValuesValidation,
      ItemStringValuesValidation
    )
    for {
      _      <- info("Dataset validation is enabled")
      _      <- info("Validation loads all events to RAM, so use --validation=false to skip in case of OOM")
      events <- eventStream.compile.toList
      _      <- info("Validation done")
    } yield {
      validators.flatMap(v => v.validate(conf, events))
    }

  }
}
