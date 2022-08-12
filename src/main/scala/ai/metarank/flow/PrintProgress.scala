package ai.metarank.flow

import ai.metarank.util.Logging
import cats.effect.IO
import fs2.Pipe

object PrintProgress extends Logging {
  case class ProgressPeriod(start: Long = System.currentTimeMillis(), total: Int = 0) {
    def inc(value: Int) = copy(total = total + value)
  }

  def tap[T]: Pipe[IO, T, T] = input =>
    input.scanChunks(ProgressPeriod()) {
      case (ProgressPeriod(start, total), next) if (start < System.currentTimeMillis() - 1000) =>
        logger.info(s"imported ${total} events, perf=${1000.0 * total / (System.currentTimeMillis() - start)}rps")
        (ProgressPeriod(start, total = total + next.size), next)
      case (progress, next) =>
        (progress.inc(next.size), next)
    }
}
