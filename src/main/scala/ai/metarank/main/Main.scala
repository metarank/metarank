package ai.metarank.main

import ai.metarank.FeatureMapping
import ai.metarank.config.Config
import ai.metarank.config.CoreConfig.TrackingConfig
import ai.metarank.fstore.Persistence
import ai.metarank.main.CliArgs.{AutoFeatureArgs, ImportArgs, ServeArgs, SortArgs, StandaloneArgs, TrainArgs, ValidateArgs}
import ai.metarank.main.command.{AutoFeature, Import, Serve, Standalone, Train, Validate}
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
              CliArgs.printHelp()
            }
          )
        _ <- IO(logger.info(Logo.raw + "  ver:" + Version().getOrElse("unknown")))
        _ <- args match {
          case a: AutoFeatureArgs => AutoFeature.run(a)
          case a: SortArgs     => Sort.run(a)
          case confArgs: CliArgs.CliConfArgs =>
            for {
              confString <- IO.fromTry(
                Try(IOUtils.toString(new FileInputStream(confArgs.conf.toFile), StandardCharsets.UTF_8))
              )
              conf    <- Config.load(confString)
              _       <- sendUsageAnalytics(conf.core.tracking, AnalyticsPayload(conf, args), env)
              mapping <- IO(FeatureMapping.fromFeatureSchema(conf.features, conf.models).optimize())
              store = Persistence.fromConfig(mapping.schema, conf.state)
              _ <- confArgs match {
                case a: ServeArgs      => Serve.run(conf, store, mapping, a)
                case a: ImportArgs     => Import.run(conf, store, mapping, a)
                case a: TrainArgs      => Train.run(conf, store, mapping, a)
                case a: ValidateArgs   => Validate.run(conf, a)
                case a: StandaloneArgs => Standalone.run(conf, store, mapping, a)
              }
            } yield {}
        }
        _ <- info("My job is done, exiting.")
      } yield {
        ExitCode.Success
      }
  }

  def sendUsageAnalytics(conf: TrackingConfig, payload: AnalyticsPayload, env: Map[String, String]): IO[Unit] = {
    val envVar = env.get("METARANK_TRACKING")
    val shouldSend = (envVar, Version.isRelease) match {
      case (Some("true"), _)  => true
      case (Some("false"), _) => false
      case (_, true)          => true
      case (_, false)         => false
    }
    if (!shouldSend) {
      info(s"usage analytics disabled: METARANK_TRACKING=$envVar isRelease=${Version.isRelease}")
    } else {
      for {
        _ <- info(
          s"usage analytics enabled: env=$envVar release=${Version.isRelease} analytics=${conf.analytics} errors=${conf.errors}"
        )
        _ <- ErrorReporter.init(conf.errors)
        _ <- AnalyticsReporter.ping(conf.analytics, payload)
      } yield {}
    }
  }
}
