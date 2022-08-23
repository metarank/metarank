package ai.metarank.fstore.memory

import ai.metarank.model.Feature.PeriodicCounterFeature
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.FeatureValue.PeriodicCounterValue
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{Key, Timestamp}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemPeriodicCounter(
    config: PeriodicCounterConfig,
    cache: Cache[Key, Map[Timestamp, Long]] = Scaffeine().build()
) extends PeriodicCounterFeature {
  override def put(action: PeriodicIncrement): IO[Unit] = IO {
    cache.getIfPresent(action.key) match {
      case None =>
        cache.put(action.key, Map(action.ts.toStartOfPeriod(config.period) -> action.inc))
      case Some(counters) =>
        val timeKey = action.ts.toStartOfPeriod(config.period)
        val incremented = counters.get(timeKey) match {
          case Some(count) => count + action.inc
          case None        => action.inc.toLong
        }
        cache.put(action.key, counters + (timeKey -> incremented))
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[PeriodicCounterValue]] =
    IO(cache.getIfPresent(key).map(values => PeriodicCounterValue(key, ts, fromMap(values))))

}
