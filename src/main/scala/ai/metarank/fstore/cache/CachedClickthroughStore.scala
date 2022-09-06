package ai.metarank.fstore.cache

import ai.metarank.fstore.Persistence.ClickthroughStore
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, EventId, Identifier, ItemValue}
import cats.effect.IO

case class CachedClickthroughStore(fast: ClickthroughStore, slow: ClickthroughStore) extends ClickthroughStore {
  override def put(cts: List[ClickthroughValues]): IO[Unit] =
    fast.put(cts) *> slow.put(cts)

  override def getall(): fs2.Stream[IO, ClickthroughValues] =
    slow.getall()

}
