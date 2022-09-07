package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence.KVStore
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemKVStore[K, V <: AnyRef](cache: Cache[K, V] = Scaffeine().build[K, V]()) extends KVStore[K, V] {
  override def get(keys: List[K]): IO[Map[K, V]] =
    IO(keys.flatMap(key => cache.getIfPresent(key).map(v => key -> v)).toMap)

  override def put(values: Map[K, V]): IO[Unit] = IO {
    values.foreach { case (k, v) => cache.put(k, v) }
  }
}
