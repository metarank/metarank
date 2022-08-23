package ai.metarank.fstore.cache

import ai.metarank.fstore.Persistence.KVStore
import cats.effect.IO

case class CachedKVStore[K, V](fast: KVStore[K, V], slow: KVStore[K, V]) extends KVStore[K, V] {
  override def get(keys: List[K]): IO[Map[K, V]] = for {
    cached <- fast.get(keys)
    missing = keys.filterNot(cached.contains)
    loaded <- slow.get(missing)
  } yield {
    cached ++ loaded
  }

  override def put(values: Map[K, V]): IO[Unit] =
    fast.put(values) *> slow.put(values)
}
