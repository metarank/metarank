package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence.KVStore
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

case class MemKVStore[V](cache: Cache[String, V] = Scaffeine().build[String, V]()) extends KVStore[V] {
  override def get(keys: List[String]): IO[Map[String, V]] =
    IO(keys.flatMap(key => cache.getIfPresent(key).map(v => key -> v)).toMap)

  override def put(values: Map[String, V]): IO[Unit] = IO {
    values.foreach { case (k, v) => cache.put(k, v) }
  }
}
