package ai.metarank

import ai.metarank.mode.bootstrap.Bootstrap
import ai.metarank.mode.inference.Inference
import ai.metarank.mode.train.Train
import ai.metarank.mode.upload.Upload
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = args match {
    case "bootstrap" :: tail => Bootstrap.run(tail)
    case "inference" :: tail => Inference.run(tail)
    case "train" :: tail     => Train.run(tail)
    case "upload" :: tail    => Upload.run(tail)
    case "help" :: _         => printHelp()
    case Nil                 => printHelp()

    case other =>
      IO.raiseError(
        new IllegalArgumentException(s"sub-command $other is not supported. Run 'metarank help' for details.")
      )
  }

  def printHelp() = for {
    _ <- IO(logger.info("Usage: metarank <command> <options>\n"))
    _ <- IO(logger.info("Supported commands: bootstrap, inference, train, upload, help."))
    _ <- IO(logger.info("Run 'metarank <command> for extra options. "))
    _ <- IO(logger.info("- bootstrap: import historical data"))
    _ <- IO(logger.info("- train: train the ranking ML model"))
    _ <- IO(logger.info("- upload: push latest feature values to redis"))
    _ <- IO(logger.info("- inference: run the inference API"))
    _ <- IO(logger.info("- help: this help"))
  } yield { ExitCode.Success }

}
