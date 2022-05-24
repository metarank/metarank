package ai.metarank.mode

import ai.metarank.FeatureMapping
import ai.metarank.config.{Config, MPath}
import ai.metarank.util.Logging
import cats.effect.{ExitCode, IO, IOApp}

import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets

trait CliApp extends IOApp with Logging {
  def usage: String

  def run(args: List[String], env: Map[String, String], config: Config, mapping: FeatureMapping): IO[ExitCode]

  override def run(args: List[String]): IO[ExitCode] = {
    args match {
      case configPath :: Nil =>
        for {
          env          <- IO { System.getenv().asScala.toMap }
          confContents <- FileLoader.read(MPath(configPath), env)
          config       <- Config.load(new String(confContents, StandardCharsets.UTF_8))
          mapping      <- IO.pure { FeatureMapping.fromFeatureSchema(config.features, config.models) }
          result       <- run(args, env, config, mapping)
        } yield {
          result
        }
      case _ => IO(logger.info(usage)) *> IO.pure(ExitCode.Error)
    }
  }

}
