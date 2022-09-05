package ai.metarank.config

import ai.metarank.config.CoreConfig.{ClickthroughJoinConfig, TrackingConfig}
import io.circe.Decoder

import scala.concurrent.duration._

case class CoreConfig(
    tracking: TrackingConfig = TrackingConfig(),
    clickthrough: ClickthroughJoinConfig = ClickthroughJoinConfig()
)

object CoreConfig {
  import ai.metarank.util.DurationJson._
  case class TrackingConfig(analytics: Boolean = true, errors: Boolean = true)
  case class ClickthroughJoinConfig(maxLength: FiniteDuration = 30.minutes, bufferSize: Int = 10000)

  implicit val clickthroughJoinConfigDecoder: Decoder[ClickthroughJoinConfig] = Decoder.instance(c =>
    for {
      maxLength  <- c.downField("maxLength").as[Option[FiniteDuration]]
      bufferSize <- c.downField("bufferSize").as[Option[Int]]
    } yield {
      ClickthroughJoinConfig(maxLength.getOrElse(30.minutes), bufferSize.getOrElse(4096))
    }
  )

  implicit val trackingConfigDecoder: Decoder[TrackingConfig] = Decoder.instance(c =>
    for {
      analytics <- c.downField("analytics").as[Option[Boolean]]
      errors    <- c.downField("errors").as[Option[Boolean]]
    } yield {
      TrackingConfig(analytics = analytics.getOrElse(true), errors = errors.getOrElse(true))
    }
  )

  implicit val coreConfigDecoder: Decoder[CoreConfig] = Decoder.instance(c =>
    for {
      tracking     <- c.downField("tracking").as[Option[TrackingConfig]]
      clickthrough <- c.downField("clickthrough").as[Option[ClickthroughJoinConfig]]
    } yield {
      CoreConfig(tracking.getOrElse(TrackingConfig()), clickthrough.getOrElse(ClickthroughJoinConfig()))
    }
  )
}
