package ai.metarank.util.analytics

import ai.metarank.main.Constants
import ai.metarank.util.{Logging, Version}
import cats.effect.IO
import io.sentry.SentryOptions.{BeforeBreadcrumbCallback, BeforeSendCallback}
import io.sentry.{Breadcrumb, Hint, Sentry, SentryEvent, SentryOptions}

object ErrorReporter extends Logging {
  def init(enabled: Boolean) = IO {
    val conf = new SentryOptions()
    logger.info(s"Sentry error reporting is ${if (enabled) "enabled" else "disabled"}")
    conf.setBeforeSend((event, _) => if (enabled) event else null)
    conf.setRelease(Version().getOrElse("snapshot"))
    conf.setBeforeBreadcrumb((_, _) => null)
    conf.setDsn(if (enabled) Constants.SENTRY_DSN else "")
    Sentry.init(conf)
  }
}
