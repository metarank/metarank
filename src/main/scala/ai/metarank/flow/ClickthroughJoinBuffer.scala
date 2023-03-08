package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.feature.BaseFeature.ValueMode
import ai.metarank.flow.ClickthroughJoinBuffer.Node
import ai.metarank.fstore.Persistence.KVStore
import ai.metarank.fstore.{ClickthroughStore, EventTicker, FeatureValueLoader}
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, FeatureValue, ItemValue, Key, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.benmanes.caffeine.cache.{RemovalCause, Ticker}
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.google.common.collect.Queues
import com.google.common.util.concurrent.MoreExecutors

import java.util
import java.util.concurrent.{BlockingQueue, ConcurrentHashMap, ConcurrentLinkedDeque, Executors}
import scala.collection.mutable.ArrayBuffer

case class ClickthroughJoinBuffer(
    cache: Cache[String, ClickthroughValues],
    queue: util.Queue[ClickthroughValues],
    ticker: EventTicker,
    values: KVStore[Key, FeatureValue],
    cts: ClickthroughStore,
    mapping: FeatureMapping,
    conf: ClickthroughJoinConfig
) extends Logging {

  def process(event: Event): IO[List[Clickthrough]] = {
    event match {
      case e: RankingEvent =>
        IO(ticker.tick(event)) *> IO.whenA(mapping.hasRankingModel)(handleRanking(e)) *> flushQueue()
      case e: InteractionEvent =>
        IO(ticker.tick(event)) *> handleInteraction(e) *> flushQueue()
      case _ =>
        IO(ticker.tick(event)) *> flushQueue()
    }
  }

  def handleRanking(event: RankingEvent): IO[Unit] = {
    val b = 1
    for {
      values  <- FeatureValueLoader.fromStateBackend(mapping, event, values)
      mvalues <- IO.fromEither(ItemValue.fromState(event, values, mapping, ValueMode.OfflineTraining))
      ctv = ClickthroughValues(
        Clickthrough(
          id = event.id,
          ts = event.timestamp,
          user = event.user,
          session = event.session,
          items = event.items.toList.map(_.id),
          rankingFields = event.fields
        ),
        mvalues.toList
      )
      _ <- IO(cache.put(ctv.ct.id.value, ctv))
    } yield {}
  }

  def handleInteraction(event: InteractionEvent): IO[Unit] = {

    event.ranking match {
      case None => // probably a rec event, flush now
        IO {
          queue.add(
            ClickthroughValues(
              Clickthrough(
                id = event.id,
                ts = event.timestamp,
                user = event.user,
                session = event.session,
                items = List(event.item),
                interactions = List(TypedInteraction(event.item, event.`type`))
              ),
              Nil
            )
          )
        }
      case Some(id) =>
        IO(cache.getIfPresent(id.value)).flatMap {
          case None =>
            // ranking already gone, nothing to do
            IO.unit
          // warn(s"ranking $id is present in interaction, but missing in cache")
          case Some(ctv) =>
            IO {
              val updated = ctv.copy(ct = ctv.ct.withInteraction(event.item, event.`type`))
              cache.put(ctv.ct.id.value, updated)
            }
        }
    }
  }

  def flushQueue(): IO[List[Clickthrough]] = {
    for {
      expired   <- IO(Iterator.continually(queue.poll()).takeWhile(_ != null).toList)
      flushable <- IO(expired.filter(_.ct.interactions.nonEmpty))
      _         <- cts.put(expired)
    } yield {
      // logger.info(s"expired $expired")
      flushable.map(_.ct)
    }
  }

  def flushAll(): IO[List[Clickthrough]] = for {
    items <- IO(cache.asMap().values)
    _     <- IO(items.foreach(ct => queue.add(ct)))
    cts   <- flushQueue()
  } yield {
    cts
  }

}

object ClickthroughJoinBuffer extends Logging {
  class Node(var payload: ClickthroughValues)
  def apply(
      conf: ClickthroughJoinConfig,
      values: KVStore[Key, FeatureValue],
      cts: ClickthroughStore,
      mapping: FeatureMapping
  ) = {
    val queue  = Queues.newConcurrentLinkedQueue[ClickthroughValues]()
    val ticker = new EventTicker()
    val cache = Scaffeine()
      .maximumSize(conf.maxParallelSessions)
      .expireAfterWrite(conf.maxSessionLength)
      .ticker(ticker)
      .evictionListener(evictionListener(queue))
      .executor(MoreExecutors.directExecutor())
      .build[String, ClickthroughValues]()
    new ClickthroughJoinBuffer(
      values = values,
      cts = cts,
      mapping = mapping,
      conf = conf,
      queue = queue,
      ticker = ticker,
      cache = cache
    )
  }

  def evictionListener(
      queue: util.Queue[ClickthroughValues]
  )(key: String, ctv: ClickthroughValues, reason: RemovalCause): Unit = {
    reason match {
      case RemovalCause.REPLACED => //
      case _                     => if (ctv.ct.interactions.nonEmpty) queue.add(ctv)
    }
  }
}
