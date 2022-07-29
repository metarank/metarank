package ai.metarank.mode

import ai.metarank.FeatureMapping
import ai.metarank.config.InputConfig.FileInputConfig
import ai.metarank.config.{Config, InputConfig, MPath}
import ai.metarank.util.Logging
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
      confContents <- IO(???) // FS.read(MPath(confPath), env)
      config       <- Config.load(new String(confContents, StandardCharsets.UTF_8))
      mapping      <- IO.pure { FeatureMapping.fromFeatureSchema(config.features, config.models) }
      result       <- run(args, env, config, mapping)
    } yield {
      result
    }
  }

}
