package ai.metarank.mode.bootstrap

import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import scopt.{OParser, OptionParser}

case class BootstrapCmdline(eventPath: String, outDir: String, config: File, parallelism: Int)

object BootstrapCmdline extends Logging {

  def parse(args: List[String], env: Map[String, String]): IO[BootstrapCmdline] = {
    val parser = new OptionParser[BootstrapCmdline]("Boostrap") {
      head("Metarank", "v0.x")

      opt[String]("events")
        .text("full path to directory containing historical events, with file:// or s3:// prefix")
        .required()
        .action((m, cmd) => cmd.copy(eventPath = m))
        .withFallback(() => env.getOrElse("METARANK_EVENTS", ""))
        .validate {
          case "" => Left("events dir is required")
          case _  => Right({})
        }

      opt[String]("out")
        .text("output directory")
        .required()
        .action((m, cmd) => cmd.copy(outDir = m))
        .withFallback(() => env.getOrElse("METARANK_OUT", ""))
        .validate {
          case "" => Left("out dir is required")
          case _  => Right({})
        }

      opt[String]("config")
        .required()
        .text("config file")
        .action((m, cmd) => cmd.copy(config = File(m)))
        .withFallback(() => env.getOrElse("METARANK_CONFIG", ""))
        .validate {
          case "" => Left("config is required")
          case _  => Right({})
        }

      opt[Int]("parallelism")
        .optional()
        .text("parallelism for a bootstrap job, 1 by default")
        .action((v, cmd) => cmd.copy(parallelism = v))
        .withFallback(() => env.getOrElse("METARANK_PARALLELISM", "1").toInt)
    }

    for {
      cmd <- IO.fromOption(parser.parse(args, BootstrapCmdline("", "", null, 1)))(
        new IllegalArgumentException("cannot parse cmdline")
      )
      _ <- IO(logger.info(s"Event path dir: ${cmd.eventPath}"))
      _ <- IO(logger.info(s"Output dir: ${cmd.outDir}"))
    } yield {
      cmd
    }
  }
}
