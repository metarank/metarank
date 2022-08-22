package ai.metarank.fstore.cache

import ai.metarank.fstore.Persistence.ClickthroughStore
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, EventId, Identifier, ItemValue}
import cats.effect.IO

case class CachedClickthroughStore(fast: ClickthroughStore, slow: ClickthroughStore) extends ClickthroughStore {
  override def putRanking(ranking: Event.RankingEvent): IO[Unit] =
    fast.putRanking(ranking) *> slow.putRanking(ranking)

  override def putValues(id: EventId, values: List[ItemValue]): IO[Unit] =
    fast.putValues(id, values) *> slow.putValues(id, values)

  override def putInteraction(id: EventId, item: Identifier.ItemId, tpe: String): IO[Unit] =
    fast.putInteraction(id, item, tpe) *> slow.putInteraction(id, item, tpe)

  override def getall(): fs2.Stream[IO, ClickthroughValues] =
    slow.getall()

  override def getClickthrough(id: EventId): IO[Option[Clickthrough]] =
    fast.getClickthrough(id).flatMap {
      case Some(ct) => IO.pure(Some(ct))
      case None     => slow.getClickthrough(id)
    }
}
