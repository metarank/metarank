package ai.metarank.util

import cats.effect.IO
import org.slf4j.LoggerFactory

trait Logging {
  protected val logger = LoggerFactory.getLogger(getClass)

  def debug(msg: String): IO[Unit]                = IO(logger.debug(msg))
  def info(msg: String): IO[Unit]                 = IO(logger.info(msg))
  def warn(msg: String): IO[Unit]                 = IO(logger.warn(msg))
  def error(msg: String, ex: Throwable): IO[Unit] = IO(logger.error(msg, ex))
}
