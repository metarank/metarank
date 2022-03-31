package ai.metarank.mode.validate

import ai.metarank.util.Logging
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}

object Validate extends IOApp with Logging {
  case class ValidationError(msg: String) extends Exception(msg)

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- args match {
        case "--config" :: configPath :: Nil => checkConfig(File(configPath))
        case "--data" :: dataPath :: Nil     => checkData(File(dataPath))
        case "--help" :: Nil                 => printHelp()
        case Nil                             => printHelp()
        case other =>
          IO.raiseError(new IllegalArgumentException(s"argument $other is not supported, use '--help' for help"))
      }
    } yield { ExitCode.Success }
  }

  def printHelp(): IO[Unit] = IO {
    logger.info("Metarank validator tool")
    logger.info("Usage: metarank validate <options>")
    logger.info("")
    logger.info("Possible options:")
    logger.info(" --config <path>       - Validate feature configuration file")
    logger.info(" --data <path>         - Validate historical events dataset")
    logger.info(" --help                - This help")
  }

  def checkConfig(cfg: File): IO[Unit] = ConfigValidator.check(cfg.contentAsString) match {
    case CheckResult.SuccessfulCheck     => IO { logger.info("Config file is valid") }
    case CheckResult.FailedCheck(reason) => IO.raiseError(ValidationError(reason))
  }
  def checkData(ds: File): IO[Unit] = EventFileValidator.check(ds) match {
    case CheckResult.SuccessfulCheck     => IO { logger.info("Data file is valid") }
    case CheckResult.FailedCheck(reason) => IO.raiseError(ValidationError(reason))
  }
}
