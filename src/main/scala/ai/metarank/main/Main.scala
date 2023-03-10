package ai.metarank.main

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.CoreConfig.TrackingConfig
import ai.metarank.fstore.{TrainStore, Persistence}
import ai.metarank.main.CliArgs.{
  AutoFeatureArgs,
  ExportArgs,
  ImportArgs,
  ServeArgs,
  SortArgs,
  StandaloneArgs,
  TrainArgs,
  ValidateArgs
}
import ai.metarank.main.command.{AutoFeature, Export, Import, Serve, Standalone, Train, Validate}
import ai.metarank.model.AnalyticsPayload
import ai.metarank.util.analytics.{AnalyticsReporter, ErrorReporter}
import ai.metarank.util.{Logging, Version}
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
        env <- IO(System.getenv().asScala.toMap)
        args <- IO
          .fromEither(CliArgs.parse(args, env))
          .onError(ex =>
            IO {
              logger.error(s"Cannot parse args: ${ex.getMessage}\n\n")
            }
          )
        _ <- info("Metarank v" + Version().getOrElse("unknown") + " is starting.")
        _ <- args match {
          case a: AutoFeatureArgs =>
            for {
              tracking <- TrackingConfig.fromEnv(env)
              _        <- sendUsageAnalytics(tracking, AnalyticsPayload(args))
              _        <- AutoFeature.run(a)
            } yield {}
          case a: SortArgs => Sort.run(a)
          case confArgs: CliArgs.CliConfArgs =>
            for {
              confString <- IO.fromTry(
                Try(IOUtils.toString(new FileInputStream(confArgs.conf.toFile), StandardCharsets.UTF_8))
              )
              conf    <- Config.load(confString, env)
              _       <- sendUsageAnalytics(conf.core.tracking, AnalyticsPayload(conf, args))
              mapping <- IO(FeatureMapping.fromFeatureSchema(conf.features, conf.models))
              store = Persistence.fromConfig(mapping.schema, conf.state, conf.core.`import`.cache)
              cts   = TrainStore.fromConfig(conf.train)
              _ <- confArgs match {
                case a: ServeArgs      => Serve.run(conf, store, cts, mapping, a)
                case a: ImportArgs     => Import.run(conf, store, cts, mapping, a)
                case a: TrainArgs      => Train.run(conf, store, cts, mapping, a)
                case a: ValidateArgs   => Validate.run(conf, a)
                case a: StandaloneArgs => Standalone.run(conf, store, cts, mapping, a)
                case a: ExportArgs     => Export.run(conf, cts, mapping, a)
              }
            } yield {}
        }
        _ <- info("My job is done, exiting.")
      } yield {
        ExitCode.Success
      }
  }

  def sendUsageAnalytics(conf: TrackingConfig, payload: AnalyticsPayload): IO[Unit] = {
    for {
      _ <- info(
        s"usage analytics enabled: release=${Version.isRelease} analytics=${conf.analytics} errors=${conf.errors}"
      )
      _ <- IO.whenA(conf.analytics)(AnalyticsReporter.ping(conf.analytics, payload))
      _ <- IO.whenA(conf.errors)(ErrorReporter.init(conf.errors))
    } yield {}
  }
}
