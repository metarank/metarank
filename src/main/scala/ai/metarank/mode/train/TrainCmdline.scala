package ai.metarank.mode.train

import ai.metarank.util.Logging
import cats.effect.IO
import scopt.OParser

case class TrainCmdline(eventPath: String, outDir: String, config: String)

object TrainCmdline extends Logging {
  val builder = OParser.builder[TrainCmdline]
  val parser = {
    import builder._
    OParser.sequence(
      programName("Metarank bootstrap cli"),
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
        .action((m, cmd) => cmd.copy(config = m))
    )
  }

  def parse(args: List[String]): IO[TrainCmdline] = for {
    cmd <- IO.fromOption(OParser.parse(parser, args, TrainCmdline("", "", "")))(
      new IllegalArgumentException("cannot parse cmdline")
    )
    _ <- IO(logger.info(s"Event path dir: ${cmd.eventPath}"))
    _ <- IO(logger.info(s"Output dir: ${cmd.outDir}"))
  } yield {
    cmd
  }
}
