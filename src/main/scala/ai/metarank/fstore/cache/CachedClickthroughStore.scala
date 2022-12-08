package ai.metarank.fstore.cache

import ai.metarank.fstore.ClickthroughStore
import ai.metarank.model.ClickthroughValues
import cats.effect.IO

case class CachedClickthroughStore(fast: ClickthroughStore, slow: ClickthroughStore) extends ClickthroughStore {
  override def put(cts: List[ClickthroughValues]): IO[Unit] =
    fast.put(cts) *> slow.put(cts)

  override def flush(): IO[Unit] = fast.flush() *> slow.flush()

  override def getall(): fs2.Stream[IO, ClickthroughValues] =
    slow.getall()

}
