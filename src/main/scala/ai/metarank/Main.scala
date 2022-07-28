package ai.metarank

import ai.metarank.mode.api.Api
import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.mode.standalone.Standalone
import ai.metarank.mode.train.Train
import ai.metarank.mode.update.Update
import ai.metarank.mode.validate.Validate
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = args match {
    case "bootstrap" :: tail  => Bootstrap.run(tail)
    case "inference" :: tail  => Standalone.run(tail)
    case "standalone" :: tail => Standalone.run(tail)
    case "train" :: tail      => Train.run(tail)
    case "validate" :: tail   => Validate.run(tail)
    case "api" :: tail        => Api.run(tail)
    case "update" :: tail     => Update.run(tail)
    case "help" :: _          => printHelp()
    case Nil                  => printHelp()

    case other =>
      IO.raiseError(
        new IllegalArgumentException(s"sub-command $other is not supported. Run 'metarank help' for details.")
      )
  }

  def printHelp() = for {
    _ <- IO(logger.info("Usage: metarank <command> <options>\n"))
    _ <- IO(logger.info("Supported commands: bootstrap, train, upload, api, update, standalone, validate, help."))
    _ <- IO(logger.info("Run 'metarank <command> for extra options. "))
    _ <- IO(logger.info("- bootstrap: import historical data"))
    _ <- IO(logger.info("- train: train the ranking ML model"))
    _ <- IO(logger.info("- upload: push latest feature values to redis"))
    _ <- IO(logger.info("- api: run the inference API"))
    _ <- IO(logger.info("- update: run the Flink update job"))
    _ <- IO(logger.info("- standalone: run the Flink update job, embedded redis and API in the same JVM"))
    _ <- IO(logger.info("- validate: check config and data files for consistency"))
    _ <- IO(logger.info("- help: this help"))
  } yield { ExitCode.Success }

}
