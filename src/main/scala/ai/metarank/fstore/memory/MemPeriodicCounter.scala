package ai.metarank.fstore.memory

import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Feature.PeriodicCounterFeature.{PeriodicCounterConfig, TimestampLongMap}
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{Key, Timestamp}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemPeriodicCounter(
    config: PeriodicCounterConfig,
    cache: Cache[Key, AnyRef] = Scaffeine().build()
) extends PeriodicCounterFeature {
  override def put(action: PeriodicIncrement): IO[Unit] = IO {
    cache.getIfPresent(action.key).flatMap(_.cast[Map[Timestamp, Long]]) match {
      case None =>
        cache.put(action.key, Map(action.ts.toStartOfPeriod(config.period) -> Long.box(action.inc)))
      case Some(counters) =>
        val timeKey = action.ts.toStartOfPeriod(config.period)
        val incremented = counters.get(timeKey) match {
          case Some(count) => count + action.inc
          case None        => action.inc.toLong
        }
        cache.put(action.key, counters + (timeKey -> Long.box(incremented)))
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[PeriodicCounterValue]] =
    IO(
      cache
        .getIfPresent(key)
        .flatMap(_.cast[Map[Timestamp, Long]])
        .map(values => PeriodicCounterValue(key, ts, fromMap(TimestampLongMap(values.toList))))
    )

}
