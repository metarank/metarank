package ai.metarank.mode.train

import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import scopt.OptionParser

case class TrainCmdline(
    input: File,
    split: Int,
    iterations: Int,
    output: File,
    config: File,
    model: String
)

object TrainCmdline extends Logging {

  def parse(args: List[String], env: Map[String, String]): IO[TrainCmdline] = {
    val parser = new OptionParser[TrainCmdline]("Train") {
      head("Metarank", "v0.x")

      opt[String]("input")
        .text(
          "path to /dataset directory after the bootstrap, only file:/// format is supported, so the data should be local"
        )
        .required()
        .action((m, cmd) => cmd.copy(input = File(m)))
        .withFallback(() => env.getOrElse("METARANK_INPUT", ""))
        .validate {
          case "" => Left("input dir is required")
          case _  => Right({})
        }

      opt[String]("model")
        .text("configured model name")
        .required()
        .action((m, cmd) => cmd.copy(model = m))
        .withFallback(() => env.getOrElse("METARANK_MODEL", ""))
        .validate {
          case "" => Left("model is required")
          case _  => Right({})
        }

      opt[Int]("split")
        .text("train/validation split in percent, default 80/20")
        .optional()
        .action((m, cmd) => cmd.copy(split = m))
        .withFallback(() => env.getOrElse("METARANK_SPLIT", "80").toInt)

      opt[Int]("iterations")
        .text("number of iterations for model training, default 200")
        .optional()
        .action((m, cmd) => cmd.copy(iterations = m))
        .withFallback(() => env.getOrElse("METARANK_ITERATIONS", "200").toInt)

      opt[String]("config")
        .text("config file with feature definition")
        .required()
        .action((m, cmd) => cmd.copy(config = File(m)))
        .withFallback(() => env.getOrElse("METARANK_CONFIG", ""))
        .validate {
          case "" => Left("config is required")
          case _  => Right({})
        }

    }

    for {
      cmd <- IO.fromOption(
        parser.parse(
          args,
          TrainCmdline(null, 80, 200, File("out.model"), File("config.yml"), "")
        )
      )(
        new IllegalArgumentException("cannot parse cmdline")
      )
      _ <- IO(logger.info(s"Input dir: ${cmd.input}"))
      _ <- IO(logger.info(s"split: ${cmd.split}"))
      _ <- IO(logger.info(s"model name:: ${cmd.model}"))
      _ <- IO(logger.info(s"model out file: ${cmd.output}"))
      _ <- IO(logger.info(s"iterations: ${cmd.iterations}"))
    } yield {
      cmd
    }
  }

}
