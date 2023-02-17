package ai.metarank.fstore.cache

import ai.metarank.fstore.Persistence.KVStore
import cats.effect.IO
import com.github.blemale.scaffeine.Cache

case class NegCachedKVStore[K, V](slow: KVStore[K, V], cache: Cache[K, Option[V]]) extends KVStore[K, V] {
  override def get(keys: List[K]): IO[Map[K, V]] = for {
    cached     <- IO(cache.getAllPresent(keys))
    cachedKeys <- IO(cached.keySet)
    missing    <- IO(keys.filterNot(cachedKeys.contains))
    loaded     <- slow.get(missing)
    empty      <- IO(missing.filterNot(loaded.contains))
    _          <- IO(cache.putAll(empty.map(k => k -> None).toMap))
    _          <- IO(cache.putAll(loaded.map(kv => kv._1 -> Some(kv._2))))
  } yield {
    loaded
  }

  override def put(values: Map[K, V]): IO[Unit] =
    IO(cache.putAll(values.map(kv => kv._1 -> Some(kv._2)))) *> slow.put(values)
}
