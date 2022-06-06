package ai.metarank.mode

import ai.metarank.FeatureMapping
import ai.metarank.config.EventSourceConfig.FileSourceConfig
import ai.metarank.config.{Config, EventSourceConfig, MPath}
import ai.metarank.util.Logging
import ai.metarank.util.fs.FS
import cats.effect.{ExitCode, IO, IOApp}

import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets

trait CliApp extends IOApp with Logging {
  def usage: String

  def run(args: List[String], env: Map[String, String], config: Config, mapping: FeatureMapping): IO[ExitCode]

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      env <- IO { System.getenv().asScala.toMap }
      confPath <- IO.fromOption(args.headOption.orElse(env.get("METARANK_CONFIG")))(
        new Exception(s"config cannot be loaded. $usage")
      )
      confContents <- FS.read(MPath(confPath), env)
      configRaw    <- Config.load(new String(confContents, StandardCharsets.UTF_8))
      config       <- IO { confOverride(configRaw, env) }
      mapping      <- IO.pure { FeatureMapping.fromFeatureSchema(config.features, config.models) }
      result       <- run(args, env, config, mapping)
    } yield {
      result
    }
  }

  def confOverride(conf: Config, env: Map[String, String]): Config = {
    env.foreach {
      case (key, value) if key.startsWith("METARANK_") => logger.warn(s"config env override: $key=$value")
      case _                                           => // nothing
    }
    conf.copy(bootstrap =
      conf.bootstrap.copy(
        workdir = env.get("METARANK_WORKDIR").map(MPath.apply).getOrElse(conf.bootstrap.workdir),
        source = conf.bootstrap.source match {
          case file: FileSourceConfig =>
            file.copy(path = env.get("METARANK_BOOTSTRAP_PATH").map(MPath.apply).getOrElse(file.path))
          case other => other
        }
      )
    )
  }

}
