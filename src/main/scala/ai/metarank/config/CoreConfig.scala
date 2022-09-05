package ai.metarank.config

import ai.metarank.config.CoreConfig.TrackingConfig
import io.circe.Decoder

case class CoreConfig(tracking: TrackingConfig = TrackingConfig())

object CoreConfig {
  case class TrackingConfig(analytics: Boolean = true, errors: Boolean = true)

  implicit val trackingConfigDecoder: Decoder[TrackingConfig] = Decoder.instance(c =>
    for {
      analytics <- c.downField("analytics").as[Option[Boolean]]
      errors    <- c.downField("errors").as[Option[Boolean]]
    } yield {
      TrackingConfig(
        analytics = analytics.getOrElse(true),
        errors = errors.getOrElse(true))
    }
  )

  implicit val coreConfigDecoder: Decoder[CoreConfig] = Decoder.instance(c =>
    for {
      tracking <- c.downField("tracking").as[Option[TrackingConfig]]
    } yield {
      CoreConfig(tracking.getOrElse(TrackingConfig()))
    }
  )
}
