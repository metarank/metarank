package ai.metarank.fstore.memory

import ai.metarank.model.Feature.MapFeature
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.FeatureValue.MapValue
import ai.metarank.model.{Key, Scalar, Timestamp, Write}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemMapFeature(config: MapConfig, cache: Cache[Key, Map[String, Scalar]] = Scaffeine().build())
    extends MapFeature {
  override def put(action: Write.PutTuple): IO[Unit] = IO {
    val map = cache
      .getIfPresent(action.key)
      .getOrElse(Map.empty)
    action.value match {
      case Some(value) =>
        cache.put(action.key, map + (action.mapKey -> value))
      case None =>
        cache.put(action.key, map - action.mapKey)
    }

  }

  override def computeValue(key: Key, ts: Timestamp): IO[Option[MapValue]] =
    IO(cache.getIfPresent(key).filter(_.nonEmpty).map(s => MapValue(key, ts, s)))
}
