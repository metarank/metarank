package me.dfdx.metarank

import java.util.concurrent.Executors

import better.files.File
import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import me.dfdx.metarank.api.HealthcheckService
import me.dfdx.metarank.config.{CommandLineConfig, Config}
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  lazy val threadpool = Executors.newWorkStealingPool(4)
  lazy val executor   = ExecutionContext.fromExecutorService(threadpool)

  override def run(args: List[String]): IO[ExitCode] =
    for {
      logger  <- Slf4jLogger.create[IO]
      cmdline <- parseCommandLine(args)
      _       <- logger.info(s"Loading config from ${cmdline.config}")
      config  <- loadConfig(cmdline.config)
      _       <- logger.info("Starting Metarank API")
      exit    <- serveRequests(config)
    } yield {
      exit
    }

  def serveRequests(config: Config): IO[ExitCode] = {
    val services = HealthcheckService.route
    val httpApp  = Router("/" -> services).orNotFound
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
