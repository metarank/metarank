package ai.metarank.util

import ai.metarank.main.Constants
import cats.effect.IO
import io.sentry.{Hint, Sentry, SentryEvent, SentryOptions}
import io.sentry.SentryOptions.BeforeSendCallback

object ErrorReporter extends Logging {
  def init(enabled: Boolean) = IO {
    val conf = new SentryOptions()
    logger.info(s"Sentry error reporting is ${if (enabled) "enabled" else "disabled"}")
    conf.setBeforeSend(new BeforeSendCallback {
      override def execute(event: SentryEvent, hint: Hint): SentryEvent =
        if (enabled) event else null
    })
    conf.setRelease(Version().getOrElse("snapshot"))
    conf.setMaxBreadcrumbs(0)
    conf.setDsn(if (enabled) Constants.SENTRY_DSN else "")
    Sentry.init(conf)
  }
}
