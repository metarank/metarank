package me.dfdx.metarank

import better.files.File
import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import me.dfdx.metarank.config.{CommandLineConfig, Config}
import me.dfdx.metarank.feature.FeatureRegistry
import org.http4s.HttpRoutes
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router

import scala.concurrent.ExecutionContext

trait RestIOApp extends IOApp {
  def executor: ExecutionContext
  def serviceName: String
  def services: HttpRoutes[IO]

  override def run(args: List[String]): IO[ExitCode] =
    for {
      logger  <- Slf4jLogger.create[IO]
      cmdline <- parseCommandLine(args)
      _       <- logger.info(s"Loading config from ${cmdline.config}")
      config  <- loadConfig(cmdline.config)
      //features <- IO { FeatureRegistry.fromConfig(config.featurespace) }
      _    <- logger.info(s"Starting Metarank $serviceName")
      exit <- serveRequests(config)
    } yield {
      exit
    }

  def serveRequests(config: Config): IO[ExitCode] = {
    val httpApp = Router("/" -> services).orNotFound
    BlazeServerBuilder[IO](executor)
      .bindHttp(config.core.listen.port, config.core.listen.hostname)
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }

  def loadConfig(file: File): IO[Config] = {
    IO.fromEither(Config.load(file.contentAsString))
  }

  def parseCommandLine(args: List[String]): IO[CommandLineConfig] = {
    IO.fromEither(CommandLineConfig.parse(args))
  }

}
