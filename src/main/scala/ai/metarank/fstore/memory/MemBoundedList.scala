package ai.metarank.fstore.memory

import ai.metarank.model.Feature.BoundedList
import ai.metarank.model.Feature.BoundedList.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.{Key, Timestamp}
import ai.metarank.model.Write.Append
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemBoundedList(config: BoundedListConfig, cache: Cache[Key, List[TimeValue]] = Scaffeine().build())
    extends BoundedList {
  override def put(action: Append): IO[Unit] = IO {
    cache.getIfPresent(action.key) match {
      case None => cache.put(action.key, List(TimeValue(action.ts, action.value)))
      case Some(cached) =>
        val result   = TimeValue(action.ts, action.value) :: cached
        val filtered = result.filter(_.ts.isAfterOrEquals(action.ts.minus(config.duration))).take(config.count)
        cache.put(action.key, filtered)
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[BoundedListValue]] =
    IO(cache.getIfPresent(key).map(BoundedListValue(key, ts, _)))

}
