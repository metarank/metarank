package ai.metarank.fstore.memory

import ai.metarank.model.Feature.BoundedListFeature
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.FeatureValue.BoundedListValue
import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import ai.metarank.model.Scalar.{SDouble, SString}
import ai.metarank.model.{Key, Scalar, Timestamp}
import ai.metarank.model.Write.Append
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import shapeless.syntax.typeable._

case class MemBoundedList(config: BoundedListConfig, cache: Cache[Key, AnyRef] = Scaffeine().build())
    extends BoundedListFeature
    with Logging {
  override def put(action: Append): IO[Unit] = IO {
    cache.getIfPresent(action.key).flatMap(_.cast[List[TimeValue]]) match {
      case None =>
        val result = action.value match {
          case Scalar.SStringList(values) => values.map(s => TimeValue(action.ts, SString(s)))
          case Scalar.SDoubleList(values) => values.map(s => TimeValue(action.ts, SDouble(s)))
          case other                      => List(TimeValue(action.ts, action.value))
        }

        cache.put(action.key, result)
      case Some(cached) =>
        val result = action.value match {
          case Scalar.SStringList(values) => values.map(s => TimeValue(action.ts, SString(s))) ++ cached
          case Scalar.SDoubleList(values) => values.toList.map(s => TimeValue(action.ts, SDouble(s))) ++ cached
          case other                      => TimeValue(action.ts, action.value) :: cached
        }
        val filtered = result.filter(_.ts.isAfterOrEquals(action.ts.minus(config.duration))).take(config.count)
        cache.put(action.key, filtered)
    }
  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[BoundedListValue]] = IO {
    cache.getIfPresent(key).flatMap(_.cast[List[TimeValue]]).map(BoundedListValue(key, ts, _, config.ttl))
  }

}
