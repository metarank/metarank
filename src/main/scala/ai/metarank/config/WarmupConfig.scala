package ai.metarank.config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import scala.concurrent.duration.*

case class WarmupConfig(sampledRequests: Int = 100, duration: FiniteDuration = 10.seconds)

object WarmupConfig {
  import ai.metarank.util.DurationJson.given
  given warmupEncoder: Encoder[WarmupConfig] = deriveEncoder

  given warmupDecoder: Decoder[WarmupConfig] = Decoder.instance(c =>
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
