package ai.metarank.mode.inference

import ai.metarank.mode.FileLoader
import ai.metarank.util.Logging
import better.files.File
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.findify.featury.values.StoreCodec
import io.findify.featury.values.StoreCodec.{JsonCodec, ProtobufCodec}
import scopt.OptionParser

case class InferenceCmdline(
    port: Int = 8080,
    host: String = "0.0.0.0",
    config: String,
    redisHost: Option[String] = None,
    redisPort: Int = 6379,
    format: StoreCodec,
    batchSize: Int = 1,
    savepoint: String,
    embeddedRedis: Option[String] = None,
    parallelism: Int = 1
)

object InferenceCmdline extends Logging {

  def parse(args: List[String], env: Map[String, String]): IO[InferenceCmdline] = {
    val parser = new OptionParser[InferenceCmdline]("Inference API") {

      head("Metarank", "v0.x")
      opt[String]("savepoint-dir")
        .text("full path savepoint snapshot, the /savepoint dir after the bootstrap phase")
        .required()
        .action((m, cmd) => cmd.copy(savepoint = m))
        .withFallback(() => env.getOrElse("METARANK_SAVEPOINT_DIR", ""))
        .validate {
          case "" => Left("savepoint dir is required")
          case _  => Right({})
        }

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
          case ""                  => Left("model file is required")
          case "json" | "protobuf" => Right({})
          case other               => Left(s"format $other is not supported")
        }

      opt[String]("config")
        .text("config file with feature definition")
        .required()
        .action((m, cmd) => cmd.copy(config = m))
        .withFallback(() => env.getOrElse("METARANK_CONFIG", ""))
        .validate {
          case "" => Left("config file is required")
          case _  => Right({})
        }

      opt[Int]("port")
        .text("HTTP port to bind to, default 8080")
        .optional()
        .action((m, cmd) => cmd.copy(port = m))
        .withFallback(() => env.getOrElse("METARANK_PORT", "8080").toInt)

      opt[String]("interface")
        .text("network inferface to bind to, default is bind to all ipv4 ifaces")
        .optional()
        .action((m, cmd) => cmd.copy(host = m))
        .withFallback(() => env.getOrElse("METARANK_INTERFACE", "0.0.0.0"))

      opt[String]("redis-host")
        .text("redis host")
        .optional()
        .action((m, cmd) => if (m.nonEmpty) cmd.copy(redisHost = Some(m)) else cmd)
        .withFallback(() => env.getOrElse("METARANK_REDIS_HOST", ""))

      opt[Int]("redis-port")
        .text("redis port, 6379 by default")
        .optional()
        .action((m, cmd) => cmd.copy(redisPort = m))
        .withFallback(() => env.getOrElse("METARANK_REDIS_PORT", "6379").toInt)

      opt[Int]("batch-size")
        .text("redis batch size, default 1")
        .optional()
        .action((m, cmd) => cmd.copy(batchSize = m))
        .withFallback(() => env.getOrElse("METARANK_BATCH_SIZE", "1").toInt)

      opt[String]("embedded-redis-features-dir")
        .text(
          "path to /features directory after the bootstrap for embedded redis, like file:///tmp/data or s3://bucket/dir"
        )
        .optional()
        .action((m, cmd) => if (m.nonEmpty) cmd.copy(embeddedRedis = Some(m)) else cmd)
        .withFallback(() => env.getOrElse("METARANK_EMBEDDED_REDIS_FEATURES_DIR", ""))

      opt[Int]("parallelism")
        .optional()
        .text("parallelism for an inference job, 1 by default")
        .action((v, cmd) => cmd.copy(parallelism = v))
        .withFallback(() => env.getOrElse("METARANK_PARALLELISM", "1").toInt)

    }

    for {
      cmd <- IO.fromOption(
        parser.parse(args, InferenceCmdline(8080, "0.0.0.0", null, None, 6379, null, 0, "", None, 1))
      )(
        new IllegalArgumentException("cannot parse cmdline")
      )
      _ <- IO(logger.info(s"Port: ${cmd.port}"))
      // _ <- IO(logger.info(s"Model path: ${cmd.model}"))
    } yield {
      cmd
    }
  }

}
