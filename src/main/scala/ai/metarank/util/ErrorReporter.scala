package ai.metarank.util

import ai.metarank.main.Constants
import cats.effect.IO
import io.sentry.{Hint, Sentry, SentryEvent, SentryOptions}
import io.sentry.SentryOptions.BeforeSendCallback

object ErrorReporter extends Logging {
  def init(enabled: Boolean) = IO {
    val conf = new SentryOptions()
    if (!enabled) logger.info("error reporting is disabled")
    conf.setBeforeSend(new BeforeSendCallback {
      override def execute(event: SentryEvent, hint: Hint): SentryEvent =
        if (enabled) event else null
    })
    conf.setRelease(Version())
    conf.setDsn(if (enabled) Constants.SENTRY_DSN else "")
    Sentry.init(conf)
  }
}
