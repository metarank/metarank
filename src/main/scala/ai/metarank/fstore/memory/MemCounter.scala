package ai.metarank.fstore.memory

import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Increment
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemCounter(config: CounterConfig, cache: Cache[Key, Long] = Scaffeine().build()) extends CounterFeature {
  override def put(action: Increment): IO[Unit] = IO {
    cache.getIfPresent(action.key) match {
      case Some(counter) => cache.put(action.key, counter + action.inc)
      case None          => cache.put(action.key, action.inc)
    }

  }
  override def computeValue(key: Key, ts: Timestamp): IO[Option[CounterValue]] = IO {
    cache.getIfPresent(key).map(c => CounterValue(key, ts, c))
  }
}
