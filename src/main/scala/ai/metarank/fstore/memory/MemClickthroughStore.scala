package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence.ClickthroughStore
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, EventId, Identifier, ItemValue}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.collection.mutable

case class MemClickthroughStore(
    cache: Cache[String, ClickthroughValues] = Scaffeine().build[String, ClickthroughValues]()
) extends ClickthroughStore {

  override def putRanking(ranking: Event.RankingEvent): IO[Unit] =
    IO {
      cache.put(
        ranking.id.value,
        ClickthroughValues(Clickthrough(ranking.id, ranking.timestamp, ranking.items.toList.map(_.id)), values = Nil)
      )
    }

  override def putValues(id: EventId, values: List[ItemValue]): IO[Unit] = IO {
    cache.getIfPresent(id.value).foreach(ctv => cache.put(id.value, ctv.copy(values = values)))
  }

  override def putInteraction(id: EventId, interaction: Identifier.ItemId, tpe: String): IO[Unit] =
    IO {
      cache
        .getIfPresent(id.value)
        .foreach(ct => cache.put(id.value, ct.copy(ct = ct.ct.withInteraction(interaction, tpe))))
    }

  override def getClickthrough(id: EventId): IO[Option[Clickthrough]] =
    IO {
      cache.getIfPresent(id.value).map(_.ct)
    }

  override def getall(): fs2.Stream[IO, ClickthroughValues] =
    fs2.Stream.emits(cache.asMap().values.toList)
}
