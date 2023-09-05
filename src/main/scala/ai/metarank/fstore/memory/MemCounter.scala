package ai.metarank.fstore.memory

import ai.metarank.model.Feature.CounterFeature
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.FeatureValue.CounterValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Increment
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemCounter(config: CounterConfig, cache: Cache[Key, AnyRef] = Scaffeine().build()) extends CounterFeature {
  override def put(action: Increment): IO[Unit] = IO {
    cache.getIfPresent(action.key).flatMap(_.cast[Long]) match {
      case Some(counter) => cache.put(action.key, Long.box(counter + action.inc))
      case None          => cache.put(action.key, Long.box(action.inc))
    }

  }
  override def computeValue(key: Key, ts: Timestamp): IO[Option[CounterValue]] = IO {
    cache.getIfPresent(key).flatMap(_.cast[Long]).map(c => CounterValue(key, ts, c, config.ttl))
  }
}
