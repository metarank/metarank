package ai.metarank.config

import ai.metarank.config.CoreConfig.{ClickthroughJoinConfig, ImportConfig, TrackingConfig}
import cats.effect.IO
import io.circe.generic.semiauto.{deriveEncoder, deriveFor}
import io.circe.{Decoder, Encoder}

import scala.concurrent.duration._

case class CoreConfig(
    tracking: TrackingConfig = TrackingConfig(),
    clickthrough: ClickthroughJoinConfig = ClickthroughJoinConfig(),
    `import`: ImportConfig = ImportConfig()
)

object CoreConfig {
  import ai.metarank.util.DurationJson._
  case class TrackingConfig(analytics: Boolean = true, errors: Boolean = true)
  object TrackingConfig {
    def fromEnv(env: Map[String, String]): IO[TrackingConfig] = ConfigEnvSubst.substTracking(TrackingConfig(), env)
  }
  case class ClickthroughJoinConfig(maxSessionLength: FiniteDuration = 30.minutes, maxParallelSessions: Int = 10000)

  case class ImportConfig(cache: ImportCacheConfig = ImportCacheConfig())

  case class ImportCacheConfig(enabled: Boolean = true, size: Int = 64 * 1024)

  implicit val importCacheDecoder: Decoder[ImportCacheConfig] = Decoder.instance(c =>
    for {
      enabled <- c.downField("enabled").as[Option[Boolean]]
      size    <- c.downField("size").as[Option[Int]]
    } yield {
      val default = ImportCacheConfig()
      ImportCacheConfig(
        enabled = enabled.getOrElse(default.enabled),
        size = size.getOrElse(default.size)
      )
    }
  )

  implicit val importConfigDecoder: Decoder[ImportConfig] = Decoder.instance(c =>
    for {
      cache <- c.downField("cache").as[Option[ImportCacheConfig]]
    } yield {
      ImportConfig(cache = cache.getOrElse(ImportCacheConfig()))
    }
  )

  implicit val clickthroughJoinConfigDecoder: Decoder[ClickthroughJoinConfig] = Decoder.instance(c =>
    for {
      maxSessionLength    <- c.downField("maxSessionLength").as[Option[FiniteDuration]]
      maxParallelSessions <- c.downField("maxParallelSessions").as[Option[Int]]
    } yield {
      ClickthroughJoinConfig(maxSessionLength.getOrElse(30.minutes), maxParallelSessions.getOrElse(4096))
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
      imp          <- c.downField("import").as[Option[ImportConfig]]
    } yield {
      CoreConfig(
        tracking = tracking.getOrElse(TrackingConfig()),
        clickthrough = clickthrough.getOrElse(ClickthroughJoinConfig()),
        `import` = imp.getOrElse(ImportConfig())
      )
    }
  )
}
