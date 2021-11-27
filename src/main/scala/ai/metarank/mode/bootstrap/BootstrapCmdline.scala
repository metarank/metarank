package ai.metarank.mode.bootstrap

import ai.metarank.util.Logging
import cats.effect.IO
import scopt.OParser

case class BootstrapCmdline(eventPath: String, outDir: String, config: String, parallelism: Int)

object BootstrapCmdline extends Logging {
  val builder = OParser.builder[BootstrapCmdline]
  val parser = {
    import builder._
    OParser.sequence(
      programName("Bootstrap"),
      head("Metarank", "v0.x"),
      opt[String]("events")
        .text("full path to directory containing historical events, with file:// or s3:// prefix")
        .required()
        .action((m, cmd) => cmd.copy(eventPath = m)),
      opt[String]("out")
        .text("output directory")
        .required()
        .action((m, cmd) => cmd.copy(outDir = m)),
      opt[String]("config")
        .text("config file")
        .action((m, cmd) => cmd.copy(config = m)),
      opt[Int]("parallelism")
        .text("parallelism for a bootstrap job")
        .action((v, cmd) => cmd.copy(parallelism = v))
    )
  }

  def parse(args: List[String]): IO[BootstrapCmdline] = for {
    cmd <- IO.fromOption(OParser.parse(parser, args, BootstrapCmdline("", "", "", 1)))(
      new IllegalArgumentException("cannot parse cmdline")
    )
    _ <- IO(logger.info(s"Event path dir: ${cmd.eventPath}"))
    _ <- IO(logger.info(s"Output dir: ${cmd.outDir}"))
  } yield {
    cmd
  }
}
