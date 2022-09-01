package ai.metarank.main

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.{ImportArgs, ServeArgs, StandaloneArgs, TrainArgs, ValidateArgs}
import ai.metarank.main.command.{Import, Serve, Standalone, Train, Validate}
import ai.metarank.model.AnalyticsPayload
import ai.metarank.util.{AnalyticsReporter, Logging}
import cats.effect.{ExitCode, IO, IOApp}
import org.apache.commons.io.IOUtils

import scala.jdk.CollectionConverters._
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import scala.util.Try

object Main extends IOApp with Logging {
  override def run(args: List[String]): IO[ExitCode] = args match {
    case "--help" :: Nil | Nil => IO(CliArgs.printHelp()) *> IO.pure(ExitCode.Success)
    case _ =>
      for {
        args <- IO
          .fromEither(CliArgs.parse(args, System.getenv().asScala.toMap))
          .onError(ex =>
            IO {
              logger.error(s"Cannot parse args: ${ex.getMessage}\n\n")
              CliArgs.printHelp()
            }
          )
        confString <- IO.fromTry(Try(IOUtils.toString(new FileInputStream(args.conf.toFile), StandardCharsets.UTF_8)))
        conf       <- Config.load(confString)
        mapping    <- IO(FeatureMapping.fromFeatureSchema(conf.features, conf.models).optimize())
        store = Persistence.fromConfig(mapping.schema, conf.state)
        _ <- IO.whenA(conf.core.tracking.analytics)(AnalyticsReporter.ping(AnalyticsPayload(conf, args)))
        _ <- args match {
          case a: ServeArgs      => Serve.run(conf, store, mapping, a)
          case a: ImportArgs     => Import.run(conf, store, mapping, a)
          case a: TrainArgs      => Train.run(conf, store, mapping, a)
          case a: ValidateArgs   => Validate.run(conf, a)
          case a: StandaloneArgs => Standalone.run(conf, store, mapping, a)
        }
        _ <- info("My job is done, exiting.")
      } yield {
        ExitCode.Success
      }
  }
}
