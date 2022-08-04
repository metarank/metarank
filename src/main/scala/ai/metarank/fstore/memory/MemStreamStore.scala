package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence.StreamStore
import cats.effect.IO

import scala.collection.mutable

case class MemStreamStore[V](cache: mutable.Buffer[V] = mutable.Buffer[V]()) extends StreamStore[V] {
  override def push(values: List[V]): IO[Unit] = IO(cache.appendAll(values))
  override def getall(): fs2.Stream[IO, V]     = fs2.Stream.emits(cache)
}
