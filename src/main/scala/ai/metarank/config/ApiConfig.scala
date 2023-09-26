package ai.metarank.config

import ai.metarank.util.Logging
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import scala.concurrent.duration._

case class ApiConfig(host: Hostname = Hostname("0.0.0.0"), port: Port = Port(8080), timeout: FiniteDuration = 5.minutes)

object ApiConfig extends Logging {
  import ai.metarank.util.DurationJson._
  implicit val apiConfigDecoder: Decoder[ApiConfig] = Decoder.instance(c =>
    for {
      host <- c.downField("host").as[Option[Hostname]].map {
        case Some(value) => value
        case None =>
          logger.info("api.host is not set, binding to 0.0.0.0 as default")
          Hostname("0.0.0.0")
      }
      port <- c.downField("port").as[Option[Port]].map {
        case Some(value) => value
        case None =>
          logger.info("api.port is not set, using 8080 as default")
          Port(8080)
      }
      timeout <- c.downField("timeout").as[Option[FiniteDuration]].map {
        case Some(value) => value
        case None =>
          logger.info("api.timeout is not set, using 5 minutes as default")
          5.minutes
      }
    } yield {
      ApiConfig(host, port, timeout)
    }
  )
}
