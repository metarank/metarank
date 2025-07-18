package ai.metarank.main

import ai.metarank.config.InputConfig.FileInputConfig.SortingType
import ai.metarank.config.InputConfig.FileInputConfig.SortingType.SortByName
import ai.metarank.config.InputConfig.SourceOffset
import ai.metarank.config.InputConfig.SourceOffset.Earliest
import ai.metarank.config.SourceFormat
import ai.metarank.main.command.autofeature.rules.RuleSet
import ai.metarank.main.command.autofeature.rules.RuleSet.RuleSetType
import ai.metarank.main.command.autofeature.rules.RuleSet.RuleSetType.{AllRuleSet, StableRuleSet}
import ai.metarank.main.command.train.SplitStrategy
import ai.metarank.main.command.train.SplitStrategy.TimeSplit
import ai.metarank.source.format.JsonFormat
import ai.metarank.source.format.SnowplowFormat.{SnowplowJSONFormat, SnowplowTSVFormat}
import ai.metarank.util.{Logging, Version}
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, ValueConverter, singleArgConverter}

import java.nio.file.Path
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

sealed trait CliArgs {}
object CliArgs extends Logging {
  sealed trait CliConfArgs extends CliArgs {
    def conf: Path
  }

  case class ServeArgs(conf: Path) extends CliConfArgs
  case class ImportArgs(
      conf: Path,
      data: Path,
      offset: SourceOffset,
      format: SourceFormat,
      validation: Boolean,
      sort: SortingType
  ) extends CliConfArgs
  case class StandaloneArgs(
      conf: Path,
      data: Path,
      offset: SourceOffset,
      format: SourceFormat,
      validation: Boolean,
      sort: SortingType
  ) extends CliConfArgs
  case class TrainArgs(conf: Path, model: Option[String], split: SplitStrategy = SplitStrategy.default)
      extends CliConfArgs
  case class ValidateArgs(conf: Path, data: Path, offset: SourceOffset, format: SourceFormat, sort: SortingType)
      extends CliConfArgs
  case class SortArgs(in: Path, out: Path) extends CliArgs
  case class AutoFeatureArgs(
      data: Path,
      out: Path,
      offset: SourceOffset = SourceOffset.Latest,
      format: SourceFormat = JsonFormat,
      rules: RuleSetType = StableRuleSet,
      sort: SortingType = SortByName,
      catThreshold: Double = 0.003
  ) extends CliArgs

  case class ExportArgs(
      conf: Path,
      model: String,
      out: Path,
      sample: Double,
      split: SplitStrategy = SplitStrategy.default
  ) extends CliConfArgs

  case class TermFreqArgs(
      data: Path,
      language: String,
      fields: List[String],
      out: Path
  ) extends CliArgs

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
              sort     <- parse(parser.`import`.sort)
            } yield {
              ImportArgs(conf, data, offset, format, validate, sort)
            }
          case Some(parser.standalone) =>
            for {
              conf     <- parse(parser.standalone.config)
              data     <- parse(parser.standalone.data)
              offset   <- parse(parser.standalone.offset)
              format   <- parse(parser.standalone.format)
              validate <- parse(parser.standalone.validation)
              sort     <- parse(parser.standalone.sort)
            } yield {
              StandaloneArgs(conf, data, offset, format, validate, sort)
            }
          case Some(parser.train) =>
            for {
              conf  <- parse(parser.train.config)
              model <- parseOption(parser.train.model)
              split <- parseOption(parser.train.split)
            } yield {
              TrainArgs(conf, model, split.getOrElse(SplitStrategy.default))
            }
          case Some(parser.validate) =>
            for {
              conf   <- parse(parser.validate.config)
              data   <- parse(parser.validate.data)
              offset <- parse(parser.validate.offset)
              format <- parse(parser.validate.format)
              sort   <- parse(parser.validate.sort)
            } yield {
              ValidateArgs(conf, data, offset, format, sort)
            }
          case Some(parser.sort) =>
            for {
              data <- parse(parser.sort.data)
              out  <- parse(parser.sort.out)
              _    <- if (data == out) Left(new Exception("data argument should not be equal to out")) else Right({})
            } yield {
              SortArgs(data, out)
            }
          case Some(parser.`autofeature`) =>
            for {
              data    <- parse(parser.autofeature.data)
              out     <- parse(parser.autofeature.out)
              offset  <- parse(parser.autofeature.offset)
              format  <- parse(parser.autofeature.format)
              ruleset <- parse(parser.autofeature.ruleset)
              sort    <- parse(parser.autofeature.sort)
              mc      <- parse(parser.autofeature.catThreshold)
            } yield {
              AutoFeatureArgs(data, out, offset, format, ruleset, sort, mc)
            }
          case Some(parser.`export`) =>
            for {
              conf   <- parse(parser.`export`.config)
              model  <- parse(parser.`export`.model)
              out    <- parse(parser.`export`.out)
              sample <- parse(parser.`export`.sample)
              split  <- parseOption(parser.`export`.split)
            } yield {
              ExportArgs(conf, model, out, sample, split.getOrElse(SplitStrategy.default))
            }
          case Some(parser.termfreq) =>
            for {
              data <- parse(parser.termfreq.data)
              out <- parse(parser.termfreq.out) match {
                case Left(value)                   => Left(value)
                case Right(f) if f.toFile.exists() => Left(new Exception(s"a file ${f} already exists"))
                case Right(f)                      => Right(f)
              }
              lang   <- parseOption(parser.termfreq.language)
              fields <- parse(parser.termfreq.fields)
            } yield {
              TermFreqArgs(
                data = data,
                out = out,
                language = lang.getOrElse("english"),
                fields = fields.split(",").toList
              )
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

  def parseOption[T](option: ScallopOption[T]): Either[Throwable, Option[T]] = {
    Try(option.toOption) match {
      case Success(value) => Right(value)
      case Failure(ex)    => Left(ex)
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

    trait SplitLikeOption { this: Subcommand =>
      val split = opt[SplitStrategy](
        name = "split",
        required = false,
        default = Some(SplitStrategy.default),
        descr = "train/test splitting strategy (optional, default: time=80%, options: random=N%,time=N%,hold_last=N%)"
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
        descr = s"offset: earliest, latest, ts=1663171036, last=1h (optional, default=earliest)",
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
      val sort = opt[SortingType](
        name = "sort-files-by",
        required = false,
        descr = "how should multiple input files be sorted (optional, default: name, values: [name,last-modified]",
        default = Some(SortingType.SortByName)
      )

    }

    object serve extends Subcommand("serve") with ConfigOption {
      descr("run the inference API")
    }

    object validate extends Subcommand("validate") with ConfigOption with ImportLikeOption {
      descr("run the input data validation suite")
    }

    object train extends Subcommand("train") with ConfigOption with SplitLikeOption {
      descr("train the ML model")
      val model = opt[String](
        "model",
        required = false,
        default = None,
        short = 'm',
        descr = "model name to train"
      )
    }

    object sort extends Subcommand("sort") {
      descr("sort the dataset by timestamp")
      val data = opt[Path](
        "data",
        required = true,
        short = 'd',
        descr = "path to a file/directory with input files",
        validate = pathExists
      )

      val out = opt[Path](
        "out",
        required = true,
        descr = "path to an output file"
      )
    }

    object autofeature extends Subcommand("autofeature") with ImportLikeOption {
      descr("generate reference config based on existing data")

      val out = opt[Path](
        "out",
        required = true,
        descr = "path to an output config file"
      )

      val ruleset = opt[RuleSetType](
        name = "ruleset",
        required = false,
        descr = "set of rules to generate config: stable, all (optional, default=stable, values: [stable, all])",
        default = Some(StableRuleSet)
      )

      val catThreshold = opt[Double](
        name = "cat-threshold",
        required = false,
        descr = "min threshold of category frequency, when its considered a category (optional, default=0.003)",
        default = Some(0.003)
      )

    }

    object `import` extends Subcommand("import") with ConfigOption with ImportLikeOption {

      descr("import historical clickthrough data")
    }

    object standalone extends Subcommand("standalone") with ConfigOption with ImportLikeOption {
      descr("import, train and serve at once")
    }

    object `export` extends Subcommand("export") with ConfigOption with SplitLikeOption {
      descr("export training dataset for hyperparameter optimization")

      val model = opt[String](
        "model",
        required = true,
        short = 'm',
        descr = "model name to export data for"
      )
      val out = opt[Path](
        name = "out",
        required = true,
        descr = "a directory to export model training files",
        validate = pathExists
      )
      val sample = opt[Double](
        name = "sample",
        required = false,
        default = Some(1.0),
        descr = "sampling ratio of exported training click-through events",
        validate = between(0.0, 1.0)
      )
    }

    object termfreq extends Subcommand("termfreq") {
      descr("compute term frequencies for the BM25 field_match extractor")
      val data = opt[Path](
        "data",
        required = true,
        short = 'd',
        descr = "path to an input file",
        validate = pathExists
      )
      val out = opt[Path](
        name = "out",
        required = true,
        descr = "an file to write term-freq dict to"
      )
      val language = opt[String](
        name = "language",
        required = false,
        default = Some("english"),
        descr = "Language to use for tokenization, stemming and stopwords"
      )
      val fields = opt[String](
        name = "fields",
        required = true,
        descr = "Comma-separated list of text fields"
      )
    }

    def pathExists(path: Path) = {
      val result = path.toFile.exists()
      if (!result) {
        for {
          parent <- Option(path.getParent) if parent.toFile.isDirectory
        } {
          val dir = parent.toFile
          if (!dir.exists()) {
            throw new Exception(
              s"Path $path does not exist. Parent dir $dir is also missing. Maybe you're using a wrong path?"
            )
          } else {
            val files = dir.listFiles().toList.map(_.toString).mkString("\n - ", "\n - ", "\n")
            throw new Exception(s"Path $path does not exist, but these files do:\n $files")
          }
        }
      }
      result
    }

    def between(min: Double, max: Double)(value: Double): Boolean = {
      (value >= min) && (value <= max)
    }

    addSubcommand(`import`)
    addSubcommand(train)
    addSubcommand(serve)
    addSubcommand(standalone)
    addSubcommand(validate)
    addSubcommand(sort)
    addSubcommand(autofeature)
    addSubcommand(`export`)
    addSubcommand(termfreq)
    version(Logo.raw + " Metarank v:" + Version().getOrElse("unknown"))
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
    case other                  => throw new IllegalArgumentException(s"cannot parse $other as boolean value")
  })

  implicit val ruleSetConverter: ValueConverter[RuleSetType] = singleArgConverter({
    case "all"    => AllRuleSet
    case "stable" => StableRuleSet
    case other    => throw new IllegalArgumentException(s"cannot parse $other as a ruleset")
  })

  implicit val sortConverter: ValueConverter[SortingType] = singleArgConverter({
    case "name"          => SortingType.SortByName
    case "last-modified" => SortingType.SortByTime
    case other           => throw new IllegalArgumentException(s"cannot parse $other as a sorting method")
  })

  implicit val splitConverter: ValueConverter[SplitStrategy] = singleArgConverter(str =>
    SplitStrategy.parse(str) match {
      case Left(error)  => throw error
      case Right(value) => value
    }
  )

}
