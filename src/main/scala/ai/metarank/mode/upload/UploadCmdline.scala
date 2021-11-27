package ai.metarank.mode.upload

import ai.metarank.util.Logging
import cats.effect.IO
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}
import scopt.OParser

case class UploadCmdline(host: String, port: Int, format: StoreCodec, dir: String, batchSize: Int)

object UploadCmdline extends Logging {

  val builder = OParser.builder[UploadCmdline]
  val parser = {
    import builder._
    OParser.sequence(
      programName("Upload"),
      head("Metarank", "v0.x"),
      opt[String]("state-dir")
        .text("path to /features directory after the bootstrap, like file:///tmp/data or s3://bucket/dir")
        .required()
        .action((m, cmd) => cmd.copy(dir = m)),
      opt[String]("host")
        .text("redis host")
        .required()
        .action((m, cmd) => cmd.copy(host = m)),
      opt[Int]("port")
        .text("redis port, 6379 by default")
        .optional()
        .action((m, cmd) => cmd.copy(port = m)),
      opt[Int]("batch-size")
        .text("write batch size, 1000 by default")
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

  def parse(args: List[String]): IO[UploadCmdline] = for {
    cmd <- IO.fromOption(OParser.parse(parser, args, UploadCmdline("", 6379, JsonCodec, "", 1000)))(
      new IllegalArgumentException("cannot parse cmdline")
    )
    _ <- IO(logger.info(s"Redis URL: redis://${cmd.host}:${cmd.port}"))
    _ <- IO(logger.info(s"Format: ${cmd.format}"))
  } yield {
    cmd
  }

}
