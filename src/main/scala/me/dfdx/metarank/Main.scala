package me.dfdx.metarank

import java.util.concurrent.Executors

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import me.dfdx.metarank.api.HealthcheckService
import me.dfdx.metarank.config.Config
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router

import scala.concurrent.ExecutionContext

object Main extends IOApp {
  lazy val threadpool = Executors.newWorkStealingPool(4)
  lazy val executor   = ExecutionContext.fromExecutorService(threadpool)

  override def run(args: List[String]): IO[ExitCode] =
    for {
      logger <- Slf4jLogger.create[IO]
      _      <- logger.info("Loading config")
      config <- loadConfig(logger)
      _      <- logger.info("Starting Metarank API")
      exit   <- serveRequests(config)
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

  def loadConfig(logger: SelfAwareStructuredLogger[IO]): IO[Config] = ???

}
