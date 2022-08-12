package ai.metarank.main

import ai.metarank.config.InputConfig.SourceOffset
import ai.metarank.config.InputConfig.SourceOffset.Earliest
import ai.metarank.config.SourceFormat
import ai.metarank.source.format.JsonFormat
import ai.metarank.source.format.SnowplowFormat.{SnowplowJSONFormat, SnowplowTSVFormat}
import ai.metarank.util.Logging
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter}

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

sealed trait CliArgs {
  def conf: Path
}
object CliArgs extends Logging {
  case class ServeArgs(conf: Path)                                                          extends CliArgs
  case class ImportArgs(conf: Path, data: Path, offset: SourceOffset, format: SourceFormat) extends CliArgs

  def parse(args: List[String]): Either[Throwable, CliArgs] = {
    Try(new ArgParser(args)) match {
      case Failure(ex) =>
        Left(new Exception(ex.getMessage))
      case Success(parser) =>
        parser.subcommand match {
          case Some(parser.serve) =>
            for {
              conf <- parse(parser.serve.config)
            } yield {
              ServeArgs(conf)
            }
          case Some(parser.`import`) =>
            for {
              conf   <- parse(parser.`import`.config)
              data   <- parse(parser.`import`.data)
              offset <- parse(parser.`import`.offset)
              format <- parse(parser.`import`.format)
            } yield {
              ImportArgs(conf, data, offset, format)
            }
          case other => Left(new Exception(s"subcommand $other is not supported"))
        }
    }
  }

  def parse[T](option: ScallopOption[T]): Either[Throwable, T] = {
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
      implicit val offsetConverter: ValueConverter[SourceOffset] = singleArgConverter(conv = {
        case "earliest"                 => SourceOffset.Earliest
        case "latest"                   => SourceOffset.Earliest
        case SourceOffset.tsPattern(ts) => SourceOffset.ExactTimestamp(ts.toLong)
        case SourceOffset.durationPattern(num, suffix) =>
          SourceOffset.RelativeDuration(FiniteDuration(num.toLong, suffix))
        case other => throw new IllegalArgumentException(s"cannot parse offset $other")
      })
      implicit val formatConverter: ValueConverter[SourceFormat] = singleArgConverter(conv = {
        case "json"          => JsonFormat
        case "snowplow"      => SnowplowTSVFormat
        case "snowplow:tsv"  => SnowplowTSVFormat
        case "snowplow:json" => SnowplowJSONFormat
        case other           => throw new IllegalArgumentException(s"format $other is not supported")
      })
      val data = opt[Path](
        "data",
        required = true,
        short = 'd',
        descr = "path to a directory with input files",
        validate = pathExists
      )
      val offset = opt[SourceOffset](
        name = "offset",
        required = false,
        short = 'o',
        descr = s"offset: earliest, latest, ts=${System.currentTimeMillis() / 1000}, last=1h",
        default = Some(Earliest)
      )
      val format = opt[SourceFormat](
        name = "format",
        required = false,
        short = 'f',
        descr = "input file format: json, snowplow, snowplow:tsv, snowplow:json",
        default = Some(JsonFormat)
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
