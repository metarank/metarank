package ai.metarank.main

import ai.metarank.config.InputConfig.SourceOffset
import ai.metarank.config.InputConfig.SourceOffset.Earliest
import ai.metarank.config.SourceFormat
import ai.metarank.source.format.JsonFormat
import ai.metarank.source.format.SnowplowFormat.{SnowplowJSONFormat, SnowplowTSVFormat}
import ai.metarank.util.Logging
import org.bouncycastle.crypto.params.Argon2Parameters
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter}

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

sealed trait CliArgs {
  def conf: Path
}
object CliArgs extends Logging {
  case class ServeArgs(conf: Path)                                                                   extends CliArgs
  case class ImportArgs(conf: Path, data: Path, offset: SourceOffset, format: SourceFormat)          extends CliArgs
  case class TrainArgs(conf: Path, model: String)                                                    extends CliArgs
  case class SortArgs(conf: Path, data: Path, out: Path, offset: SourceOffset, format: SourceFormat) extends CliArgs

  def printHelp() = new ArgParser(Nil).printHelp()

  def parse(args: List[String]): Either[Throwable, CliArgs] = {
    val parser = new ArgParser(args)
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
              conf   <- parse(parser.`import`.config)
              data   <- parse(parser.`import`.data)
              offset <- parse(parser.`import`.offset)
              format <- parse(parser.`import`.format)
            } yield {
              ImportArgs(conf, data, offset, format)
            }
          case Some(parser.train) =>
            for {
              conf  <- parse(parser.train.config)
              model <- parse(parser.train.model)
            } yield {
              TrainArgs(conf, model)
            }
          case Some(parser.sort) =>
            for {
              conf   <- parse(parser.sort.config)
              data   <- parse(parser.sort.data)
              out    <- parse(parser.sort.out)
              offset <- parse(parser.sort.offset)
              format <- parse(parser.sort.format)
            } yield {
              SortArgs(conf, data, out, offset, format)
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

    object serve extends Subcommand("serve") with ConfigOption {
      descr("run the inference API")
    }

    object sort extends Subcommand("sort") with ConfigOption {
      descr("sort the input file by timestamp")
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
        short = 'o',
        descr = "output file path"
      )
      val offset = opt[SourceOffset](
        name = "offset",
        required = false,
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

    object train extends Subcommand("train") with ConfigOption {
      descr("train the ML model")
      val model = opt[String](
        "model",
        required = true,
        short = 'm',
        descr = "model name to train"
      )
    }

    object `import` extends Subcommand("import") with ConfigOption {
      descr("import historical clickthrough data")
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
    addSubcommand(train)
    addSubcommand(sort)
    version("Metarank v0.5.x")
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

}
