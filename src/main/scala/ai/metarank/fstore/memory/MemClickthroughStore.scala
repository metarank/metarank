package ai.metarank.fstore.memory

import ai.metarank.fstore.Persistence.ClickthroughStore
import ai.metarank.model.{Clickthrough, Event, EventId}
import cats.effect.IO
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.collection.mutable

case class MemClickthroughStore(cache: Cache[String, Clickthrough] = Scaffeine().build[String, Clickthrough]())
    extends ClickthroughStore {
  override def get(id: EventId): IO[Option[Clickthrough]] = IO { cache.getIfPresent(id.value) }
  override def put(event: Clickthrough): IO[Unit]         = IO { cache.put(event.ranking.id.value, event) }
  override def getall(): fs2.Stream[IO, Clickthrough]     = fs2.Stream.emits(cache.asMap().values.toList)
}
