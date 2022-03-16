package ai.metarank.mode.upload

import ai.metarank.mode.FileLoader
import ai.metarank.mode.train.TrainCmdline
import ai.metarank.util.Logging
import cats.effect.IO
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}
import scopt.{OParser, OptionParser}

case class UploadCmdline(host: String, port: Int, format: StoreCodec, dir: String, batchSize: Int, parallelism: Int) {
  lazy val dirUrl = FileLoader.makeURL(dir)
}

object UploadCmdline extends Logging {

  def parse(args: List[String], env: Map[String, String]): IO[UploadCmdline] = {
    val parser = new OptionParser[UploadCmdline]("Upload") {
      head("Metarank", "v0.x")
      opt[String]("features-dir")
        .text(
          "path to /features directory after the bootstrap (optionally with scheme file:///tmp/data or s3://bucket/dir"
        )
        .required()
        .action((m, cmd) => cmd.copy(dir = m))
        .withFallback(() => env.getOrElse("METARANK_FEATURES_DIR", ""))
        .validate {
          case "" => Left("features dir is required")
          case _  => Right({})
        }

      opt[String]("host")
        .text("redis host")
        .required()
        .action((m, cmd) => cmd.copy(host = m))
        .withFallback(() => env.getOrElse("METARANK_REDIS_HOST", ""))
        .validate {
          case "" => Left("redis host is required")
          case _  => Right({})
        }

      opt[Int]("port")
        .text("redis port, 6379 by default")
        .optional()
        .action((m, cmd) => cmd.copy(port = m))
        .withFallback(() => env.getOrElse("METARANK_REDIS_PORT", "6379").toInt)

      opt[Int]("batch-size")
        .text("write batch size, 1000 by default")
        .optional()
        .action((m, cmd) => cmd.copy(batchSize = m))
        .withFallback(() => env.getOrElse("METARANK_BATCH_SIZE", "1000").toInt)

      opt[String]("format")
        .text("state encoding format, protobuf/json")
        .required()
        .action((m, cmd) =>
          cmd.copy(format = m match {
            case "protobuf" => ProtobufCodec
            case "json"     => JsonCodec
          })
        )
        .withFallback(() => env.getOrElse("METARANK_FORMAT", ""))
        .validate {
          case ""                  => Left("redis host is required")
          case "json" | "protobuf" => Right({})
          case other               => Left(s"format $other is not supported")
        }

      opt[Int]("parallelism")
        .optional()
        .text("parallelism for an upload job, 1 by default")
        .action((v, cmd) => cmd.copy(parallelism = v))
        .withFallback(() => env.getOrElse("METARANK_PARALLELISM", "1").toInt)

    }

    for {
      cmd <- IO.fromOption(parser.parse(args, UploadCmdline("", 6379, JsonCodec, "", 1000, 1)))(
        new IllegalArgumentException("cannot parse cmdline")
      )
      _ <- IO(logger.info(s"Redis URL: redis://${cmd.host}:${cmd.port}"))
      _ <- IO(logger.info(s"Format: ${cmd.format}"))
    } yield {
      cmd
    }
  }

}
