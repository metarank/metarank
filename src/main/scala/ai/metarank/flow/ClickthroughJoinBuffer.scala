package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.flow.ClickthroughJoinBuffer.StaticTicker
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, ItemValue, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.benmanes.caffeine.cache.{RemovalCause, Scheduler, Ticker}
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.mutable.ArrayBuffer

case class ClickthroughJoinBuffer(
    rankings: Cache[String, ClickthroughValues],
    state: Persistence,
    queue: ConcurrentLinkedQueue[ClickthroughValues],
    mapping: FeatureMapping,
    ticker: StaticTicker
) extends Logging {

  def process(event: Event): IO[List[Clickthrough]] = {
    event match {
      case e: RankingEvent =>
        IO(tick(event.timestamp)) *> handleRanking(e) *> flushQueue()
      case e: InteractionEvent =>
        IO(tick(event.timestamp)) *> handleInteraction(e) *> flushQueue()
      case _ =>
        IO(tick(event.timestamp)) *> flushQueue()
    }
  }

  def handleRanking(event: RankingEvent): IO[Unit] = for {
    keys    <- IO(mapping.features.flatMap(_.valueKeys(event)))
    values  <- state.values.get(keys)
    mvalues <- IO(ItemValue.fromState(event, values, mapping))
    ctv = ClickthroughValues(
      Clickthrough(
        id = event.id,
        ts = event.timestamp,
        user = event.user,
        session = event.session,
        items = event.items.toList.map(_.id)
      ),
      mvalues
    )
    _ <- IO(rankings.put(ctv.ct.id.value, ctv))
  } yield {
    logger.info(s"added $ctv")
  }

  def handleInteraction(event: InteractionEvent): IO[Unit] = for {
    rankingOption <- IO(event.ranking.flatMap(id => rankings.getIfPresent(id.value)))
    _ <- rankingOption match {
      case None => IO.unit
      case Some(ranking) =>
        IO {
          val updated = ranking.copy(ct = ranking.ct.withInteraction(event.item, event.`type`))
          rankings.put(updated.ct.id.value, updated)
        }
    }
  } yield {
    val br = 1
  }

  def tick(now: Timestamp) = IO(ticker.advance(now)) *> IO(rankings.cleanUp())

  def flushQueue(): IO[List[Clickthrough]] = for {
    expired <- dequeue()
    _       <- state.cts.put(expired)
  } yield {
    expired.map(_.ct)
  }

  def flushAll(): IO[List[Clickthrough]] = for {
    expired <- dequeueAll()
    _       <- state.cts.put(expired)
  } yield {
    expired.map(_.ct)
  }

  def dequeue() = IO {
    val buffer = new ArrayBuffer[ClickthroughValues]()
    while (!queue.isEmpty) {
      buffer.addOne(queue.poll())
    }
    buffer.toList
  }

  def dequeueAll() = IO {
    val values = queue.iterator().asScala.toList
    queue.clear()
    values
  }
}

object ClickthroughJoinBuffer extends Logging {
  class StaticTicker extends Ticker with Logging {
    var now = 0L
    def advance(ts: Timestamp) = {
      val next = ts.ts * 1000000L
      if (next <= now) logger.warn(s"time is going backwards: now=$now next=$next")
      now = next
    }
    override def read(): Long = now
  }
  def apply(conf: ClickthroughJoinConfig, store: Persistence, mapping: FeatureMapping) = {
    val fqueue = new ConcurrentLinkedQueue[ClickthroughValues]()
    val ticker = new StaticTicker()
    new ClickthroughJoinBuffer(
      rankings = Scaffeine()
        .maximumSize(conf.bufferSize)
        .expireAfterWrite(conf.maxLength)
        .removalListener[String, ClickthroughValues]((_, value, reason) => {
          if (value.ct.interactions.nonEmpty && ((reason == RemovalCause.EXPIRED) || (reason == RemovalCause.SIZE))) {
            fqueue.add(value)
          }
        })
        .ticker(ticker)
        .build[String, ClickthroughValues](),
      state = store,
      queue = fqueue,
      mapping = mapping,
      ticker = ticker
    )
  }
}
