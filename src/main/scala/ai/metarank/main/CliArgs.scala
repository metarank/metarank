package ai.metarank.main

import ai.metarank.config.InputConfig.SourceOffset
import ai.metarank.config.InputConfig.SourceOffset.Earliest
import ai.metarank.config.SourceFormat
import ai.metarank.source.format.JsonFormat
import ai.metarank.source.format.SnowplowFormat.{SnowplowJSONFormat, SnowplowTSVFormat}
import ai.metarank.util.{Logging, Version}
import org.bouncycastle.crypto.params.Argon2Parameters
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter}

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

sealed trait CliArgs {
  def conf: Path
}
object CliArgs extends Logging {
  case class ServeArgs(conf: Path) extends CliArgs
  case class ImportArgs(conf: Path, data: Path, offset: SourceOffset, format: SourceFormat, validation: Boolean)
      extends CliArgs
  case class StandaloneArgs(conf: Path, data: Path, offset: SourceOffset, format: SourceFormat, validation: Boolean)
      extends CliArgs
  case class TrainArgs(conf: Path, model: String)                                             extends CliArgs
  case class ValidateArgs(conf: Path, data: Path, offset: SourceOffset, format: SourceFormat) extends CliArgs
  case class SortArgs(conf: Path, in: Path, out: Path)                                        extends CliArgs

  def printHelp() = new ArgParser(Nil, Map.empty).printHelp()

  def parse(args: List[String], env: Map[String, String]): Either[Throwable, CliArgs] = {
    val parser = new ArgParser(args, env)
    Try(parser.verify()) match {
      case Failure(ex) =>
        Left(new Exception(ex.getMessage))
      case Success(_) =>
        parser.subcommand match {
          case Some(parser.serve) =>
            for {
              conf <- parse(parser.serve.config)
            } yield {
              ServeArgs(conf)
            }
          case Some(parser.`import`) =>
            for {
              conf     <- parse(parser.`import`.config)
              data     <- parse(parser.`import`.data)
              offset   <- parse(parser.`import`.offset)
              format   <- parse(parser.`import`.format)
              validate <- parse(parser.`import`.validation)
            } yield {
              ImportArgs(conf, data, offset, format, validate)
            }
          case Some(parser.standalone) =>
            for {
              conf     <- parse(parser.standalone.config)
              data     <- parse(parser.standalone.data)
              offset   <- parse(parser.standalone.offset)
              format   <- parse(parser.standalone.format)
              validate <- parse(parser.standalone.validation)
            } yield {
              StandaloneArgs(conf, data, offset, format, validate)
            }
          case Some(parser.train) =>
            for {
              conf  <- parse(parser.train.config)
              model <- parse(parser.train.model)
            } yield {
              TrainArgs(conf, model)
            }
          case Some(parser.validate) =>
            for {
              conf   <- parse(parser.validate.config)
              data   <- parse(parser.validate.data)
              offset <- parse(parser.validate.offset)
              format <- parse(parser.validate.format)
            } yield {
              ValidateArgs(conf, data, offset, format)
            }
          case Some(parser.sort) =>
            for {
              conf <- parse(parser.sort.config)
              data <- parse(parser.sort.data)
              out  <- parse(parser.sort.out)
            } yield {
              SortArgs(conf, data, out)
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

  class ArgParser(args: List[String], env: Map[String, String]) extends ScallopConf(args) {
    trait ConfigOption {
      this: Subcommand =>
      lazy val config =
        opt[Path](
          name = "config",
          required = true,
          short = 'c',
          descr = "path to config file",
          default = env.get("METARANK_CONFIG").map(Path.of(_)),
          validate = pathExists
        )
    }

    trait ImportLikeOption { this: Subcommand =>
      val data = opt[Path](
        "data",
        required = true,
        short = 'd',
        descr = "path to an input file",
        validate = pathExists
      )
      val offset = opt[SourceOffset](
        name = "offset",
        required = false,
        short = 'o',
        descr =
          s"offset: earliest, latest, ts=${System.currentTimeMillis() / 1000}, last=1h (optional, default=earliest)",
        default = Some(Earliest)
      )
      val format = opt[SourceFormat](
        name = "format",
        required = false,
        short = 'f',
        descr = "input file format: json, snowplow, snowplow:tsv, snowplow:json (optional, default=json)",
        default = Some(JsonFormat)
      )
      val validation = opt[Boolean](
        name = "validation",
        required = false,
        descr = "should input validation be enabled (optional, default=false)",
        default = Some(false)
      )

    }

    object serve extends Subcommand("serve") with ConfigOption {
      descr("run the inference API")
    }

    object validate extends Subcommand("validate") with ConfigOption with ImportLikeOption {
      descr("run the input data validation suite")
    }

    object train extends Subcommand("train") with ConfigOption {
      descr("train the ML model")
      val model = opt[String](
        "model",
        required = true,
        short = 'm',
        descr = "model name to train"
      )
    }

    object sort extends Subcommand("sort") with ConfigOption {
      descr("sort the dataset by timestamp")
      val data = opt[Path](
        "data",
        required = true,
        short = 'd',
        descr = "path to a directory with input files",
        validate = pathExists
      )

      val out = opt[Path](
        "out",
        required = true,
        descr = "path to an output file"
      )

    }

    object `import` extends Subcommand("import") with ConfigOption with ImportLikeOption {

      descr("import historical clickthrough data")
    }

    object standalone extends Subcommand("standalone") with ConfigOption with ImportLikeOption {
      descr("import, train and serve at once")
    }

    def pathExists(path: Path) = {
      val result = path.toFile.exists()
      if (!result) logger.error(s"Path $path does not exist")
      result
    }

    addSubcommand(`import`)
    addSubcommand(train)
    addSubcommand(serve)
    addSubcommand(standalone)
    addSubcommand(validate)
    addSubcommand(sort)
    version(Logo.raw + " ver:" + Version())
    banner("""Usage: metarank <subcommand> <options>
             |Options:
             |""".stripMargin)
    footer("\nFor all other tricks, consult the docs on https://docs.metarank.ai")

    override protected def onError(e: Throwable): Unit = throw e
  }

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

  implicit val booleanConverter: ValueConverter[Boolean] = singleArgConverter({
    case "yes" | "true" | "on"  => true
    case "no" | "false" | "off" => false
    case other                  => throw new IllegalArgumentException(s"cannot parse $other as boolean valus")
  })

}
