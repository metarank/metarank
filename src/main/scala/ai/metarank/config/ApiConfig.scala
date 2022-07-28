package ai.metarank.config

import ai.metarank.util.Logging
import io.circe.Decoder

case class ApiConfig(host: Hostname, port: Port)

object ApiConfig extends Logging {
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
    } yield {
      ApiConfig(host, port)
    }
  )
}
