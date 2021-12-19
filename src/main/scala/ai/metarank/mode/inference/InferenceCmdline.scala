package ai.metarank.mode.inference

import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}
import scopt.OParser

case class InferenceCmdline(
    port: Int,
    host: String,
    model: File,
    config: File,
    redisHost: String,
    redisPort: Int,
    format: StoreCodec,
    batchSize: Int,
    savepoint: String
)

object InferenceCmdline extends Logging {
  val builder = OParser.builder[InferenceCmdline]
  val parser = {
    import builder._
    OParser.sequence(
      programName("Inference"),
      head("Metarank", "v0.x"),
      opt[String]("savepoint-dir")
        .text("full path savepoint snapshot, the /savepoint dir after the bootstrap phase")
        .required()
        .action((m, cmd) => cmd.copy(savepoint = m)),
      opt[String]("model")
        .text("full path to model file to serve")
        .required()
        .action((m, cmd) => cmd.copy(model = File(m))),
      opt[Int]("port")
        .text("HTTP port to bind to, default 8080")
        .optional()
        .action((m, cmd) => cmd.copy(port = m)),
      opt[String]("interface")
        .text("network inferface to bind to, default is bind to everything")
        .optional()
        .action((m, cmd) => cmd.copy(host = m)),
      opt[String]("config")
        .text("config file with feature definition")
        .required()
        .action((m, cmd) => cmd.copy(config = File(m))),
      opt[String]("redis-host")
        .text("redis host")
        .required()
        .action((m, cmd) => cmd.copy(redisHost = m)),
      opt[Int]("redis-port")
        .text("redis port, 6379 by default")
        .optional()
        .action((m, cmd) => cmd.copy(redisPort = m)),
      opt[Int]("batch-size")
        .text("redis batch size, default 1")
        .optional()
        .action((m, cmd) => cmd.copy(batchSize = m)),
      opt[String]("format")
        .text("state encoding format, protobuf/json")
        .required()
        .action((m, cmd) =>
          cmd.copy(format = m match {
            case "protobuf" => ProtobufCodec
            case "json"     => JsonCodec
          })
        )
    )
  }

  def parse(args: List[String]): IO[InferenceCmdline] = for {
    cmd <- IO.fromOption(OParser.parse(parser, args, InferenceCmdline(8080, "::", null, null, "", 6379, null, 0, "")))(
      new IllegalArgumentException("cannot parse cmdline")
    )
    _ <- IO(logger.info(s"Port: ${cmd.port}"))
    _ <- IO(logger.info(s"Model path: ${cmd.model}"))
  } yield {
    cmd
  }

}
