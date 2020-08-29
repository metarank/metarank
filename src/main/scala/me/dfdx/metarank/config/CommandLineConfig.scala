package me.dfdx.metarank.config

import better.files.File
import me.dfdx.metarank.config.CommandLineConfig.DEFAULT_CONFIG_FILE
import scopt.OParser

case class CommandLineConfig(
    hostname: Option[String] = None,
    port: Option[Int] = None,
    config: File = DEFAULT_CONFIG_FILE
)

object CommandLineConfig {
  val DEFAULT_CONFIG_FILE = File("/etc/metarank/metarank.yml")
  lazy val builder        = OParser.builder[CommandLineConfig]
  lazy val parser = {
    import builder._
    OParser.sequence(
      programName("Metarank"),
      head("metarank", "v0.0"),
      opt[String]('h', "hostname")
        .action((iface, conf) => conf.copy(hostname = Some(iface)))
        .valueName("<ip or iface>")
        .text("Network interface to bind to. Default: 127.0.0.1"),
      opt[Int]('p', "port")
        .action((port, conf) => conf.copy(port = Some(port)))
        .validate {
          case i if i > 0 & i < 65536 => Right({})
          case _                      => Left("Port should be in 1-65535 range")
        }
        .valueName("<num>")
        .text("Port number for REST API"),
      opt[String]('c', "config")
        .action((file, conf) => conf.copy(config = File(file)))
        .text(s"Config file. Default: ${DEFAULT_CONFIG_FILE}")
        .valueName("<file>")
        .validate(fileName =>
          File(fileName).exists match {
            case true  => Right({})
            case false => Left("config file must exist")
          }
        )
    )
  }

  def parse(args: List[String]): Either[CommandLineParsingError, CommandLineConfig] = {
    OParser.parse(parser, args, CommandLineConfig()) match {
      case Some(config) => Right(config)
      case None         => Left(CommandLineParsingError("wrong command-line arguments"))
    }
  }

  case class CommandLineParsingError(msg: String) extends Throwable
}
