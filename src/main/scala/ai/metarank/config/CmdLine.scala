package ai.metarank.config
import ai.metarank.util.Logging
import cats.effect.IO
import scopt.OParser

case class CmdLine(mode: String, config: String)

object CmdLine extends Logging {
  val builder = OParser.builder[CmdLine]
  val parser = {
    import builder._
    OParser.sequence(
      programName("Metarank"),
      head("Metarank", "v0.x"),
      arg[String]("<mode>").required().action((m, c) => c.copy(mode = m)).text("run mode"),
      opt[String]("config").required().action((c, cmd) => cmd.copy(config = c)).text("path to config file")
    )
  }

  def parse(args: List[String]): IO[CmdLine] = for {
    cmd <- IO.fromOption(OParser.parse(parser, args, CmdLine("", "")))(new Exception("cannot parse command line"))
    _   <- IO(logger.info(s"Selected mode '${cmd.mode}', config path ${cmd.config}"))
  } yield {
    cmd
  }
}
