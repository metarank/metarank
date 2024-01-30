package ai.metarank.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

import scala.concurrent.duration._

case class WarmupConfig(sampledRequests: Int = 100, duration: FiniteDuration = 10.seconds)

object WarmupConfig {
  import ai.metarank.util.DurationJson._
  implicit val warmupEncoder: Encoder[WarmupConfig] = deriveEncoder

  implicit val warmupDecoder: Decoder[WarmupConfig] = Decoder.instance(c =>
    for {
      requestsOption <- c.downField("sampledRequests").as[Option[Int]]
      durationOption <- c.downField("duration").as[Option[FiniteDuration]]
    } yield {
      WarmupConfig(
        sampledRequests = requestsOption.getOrElse(100),
        duration = durationOption.getOrElse(10.seconds)
      )
    }
  )
}
