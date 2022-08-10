package ai.metarank.main

import ai.metarank.util.Logging
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand}

import java.nio.file.Path
import scala.util.{Failure, Success, Try}

sealed trait CliArgs {
  def conf: Path
}
object CliArgs extends Logging {
  case class ServeArgs(conf: Path)              extends CliArgs
  case class ImportArgs(conf: Path, data: Path) extends CliArgs

  def parse(args: List[String]): Either[Throwable, CliArgs] = {
    Try(new ArgParser(args)) match {
      case Failure(ex) =>
        Left(new Exception(ex.getMessage))
      case Success(parser) =>
        parser.subcommand match {
          case Some(parser.serve) =>
            for {
              conf <- parseRequired(parser.serve.config)
            } yield {
              ServeArgs(conf)
            }
          case Some(parser.`import`) =>
            for {
              conf <- parseRequired(parser.`import`.config)
              data <- parseRequired(parser.`import`.data)
            } yield {
              ImportArgs(conf, data)
            }
          case other => Left(new Exception(s"subcommand $other is not supported"))
        }
    }
  }

  def parseRequired[T](option: ScallopOption[T]): Either[Throwable, T] = {
    Try(option.toOption) match {
      case Success(Some(value)) => Right(value)
      case Success(None)        => Left(new Exception(s"missing required option ${option.name}"))
      case Failure(ex)          => Left(ex)
    }
  }

  class ArgParser(args: List[String]) extends ScallopConf(args) {
    trait ConfigOption {
      this: Subcommand =>
      lazy val config =
        opt[Path]("config", required = true, short = 'c', descr = "path to config file", validate = pathExists)
    }

    object serve extends Subcommand("serve") with ConfigOption

    object `import` extends Subcommand("import") with ConfigOption {
      val data = opt[Path](
        "data",
        required = true,
        short = 'd',
        descr = "path to a directory with input files",
        validate = pathExists
      )
    }

    def pathExists(path: Path) = path.toFile.exists()

    addSubcommand(serve)
    addSubcommand(`import`)
    version("Metarank v0.5.x")
    banner("""Usage: metarank <subcommand> <options>
             |Options:
             |""".stripMargin)
    footer("\nFor all other tricks, consult the docs on https://docs.metarank.ai")
    errorMessageHandler = (message: String) => { logger.error(message) }
    verify()
  }
}
