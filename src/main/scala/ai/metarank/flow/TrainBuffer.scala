package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.feature.BaseFeature.ValueMode
import ai.metarank.fstore.Persistence.KVStore
import ai.metarank.fstore.{EventTicker, FeatureValueLoader, TrainStore}
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.model.Clickthrough.TypedInteraction
import ai.metarank.model.Event.{InteractionEvent, ItemEvent, RankItem, RankingEvent, UserEvent}
import ai.metarank.model.TrainValues.{ClickthroughValues, ItemValues, UserValues}
import ai.metarank.model.{Clickthrough, Event, FeatureValue, ItemValue, Key, TrainValues}
import ai.metarank.util.Logging
import cats.effect.IO
import com.github.benmanes.caffeine.cache.{RemovalCause, Ticker}
import com.github.blemale.scaffeine.{Cache, Scaffeine}
import com.google.common.collect.Queues
import com.google.common.util.concurrent.MoreExecutors

import java.util

case class TrainBuffer(
    cache: Cache[String, ClickthroughValues],
    queue: util.Queue[TrainValues],
    ticker: EventTicker,
    values: KVStore[Key, FeatureValue],
    cts: TrainStore,
    mapping: FeatureMapping,
    conf: ClickthroughJoinConfig,
    userItemNeeded: Boolean = true
) extends Logging {

  def process(event: Event): IO[List[TrainValues]] = {
    event match {
      case e: RankingEvent =>
        IO(ticker.tick(event)) *> IO.whenA(mapping.hasRankingModel)(handleRanking(e)) *> flushQueue()
      case e: InteractionEvent =>
        IO(ticker.tick(event)) *> handleInteraction(e) *> flushQueue()
      case e: UserEvent =>
        IO(ticker.tick(event)) *> handleUser(e) *> flushQueue()
      case e: ItemEvent =>
        IO(ticker.tick(event)) *> handleItem(e) *> flushQueue()
      case _ =>
        IO(ticker.tick(event)) *> flushQueue()
    }
  }

  def handleItem(item: ItemEvent): IO[Unit] = IO.whenA(userItemNeeded)(IO { queue.add(ItemValues(item)) })
  def handleUser(user: UserEvent): IO[Unit] = IO.whenA(userItemNeeded)(IO { queue.add(UserValues(user)) })

  def handleRanking(event: RankingEvent): IO[Unit] = for {
    values  <- FeatureValueLoader.fromStateBackend(mapping, event, values)
    mvalues <- IO.fromEither(ItemValue.fromState(event, values, mapping, ValueMode.OfflineTraining))
    ctv = ClickthroughValues(
      Clickthrough(
        id = event.id,
        ts = event.timestamp,
        user = event.user,
        session = event.session,
        items = event.items.toList.map(_.id),
        rankingFields = event.fields,
        interactions = explicitLabelInteractions(event)
      ),
      mvalues.toList
    )
    _ <- ctv.ct.interactions match {
      case Nil => IO(cache.put(ctv.ct.id.value, ctv))
      case _   => IO(queue.add(ctv))
    }
  } yield {}

  def explicitLabelInteractions(event: RankingEvent): List[TypedInteraction] = {
    event.items.toList.flatMap {
      case RankItem(id, _, Some(rel)) => Some(TypedInteraction(id, s"rel$rel", Some(rel)))
      case _                          => None
    }
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
            // ranking already gone, or it never was present
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
          // warn(s"ranking $id is present in interaction, but missing in cache")
          case Some(ctv) =>
            IO {
              val updated = ctv.copy(ct = ctv.ct.withInteraction(event.item, event.`type`))
              cache.put(ctv.ct.id.value, updated)
            }
        }
    }
  }

  def flushQueue(): IO[List[TrainValues]] = {
    for {
      expired <- IO(Iterator.continually(queue.poll()).takeWhile(_ != null).toList)
      flushable <- IO(expired.filter {
        case ClickthroughValues(ct, _) => ct.interactions.nonEmpty
        case _                         => true
      })
      _ <- cts.put(expired)
    } yield {
      // logger.info(s"expired $expired")
      flushable
    }
  }

  def flushAll(): IO[List[TrainValues]] = for {
    items <- IO(cache.asMap().values)
    _     <- IO(items.foreach(ct => queue.add(ct)))
    cts   <- flushQueue()
  } yield {
    cts
  }

}

object TrainBuffer extends Logging {
  class Node(var payload: ClickthroughValues)
  def apply(
      conf: ClickthroughJoinConfig,
      values: KVStore[Key, FeatureValue],
      cts: TrainStore,
      mapping: FeatureMapping
  ) = {
    val queue  = Queues.newConcurrentLinkedQueue[TrainValues]()
    val ticker = new EventTicker()
    val cache = Scaffeine()
      .maximumSize(conf.maxParallelSessions)
      .expireAfterWrite(conf.maxSessionLength)
      .ticker(ticker)
      .evictionListener(evictionListener(queue))
      .executor(MoreExecutors.directExecutor())
      .build[String, ClickthroughValues]()
    val userItemTrainingNeeded = mapping.models.values.toList.exists {
      case _: RecommendPredictor[_, _] => true
      case _                           => false
    }
    new TrainBuffer(
      values = values,
      cts = cts,
      mapping = mapping,
      conf = conf,
      queue = queue,
      ticker = ticker,
      cache = cache,
      userItemNeeded = userItemTrainingNeeded
    )
  }

  def evictionListener(
      queue: util.Queue[TrainValues]
  )(key: String, tv: TrainValues, reason: RemovalCause): Unit = {
    reason match {
      case RemovalCause.REPLACED => //
      case _ =>
        tv match {
          case ClickthroughValues(ct, values) if ct.interactions.nonEmpty => queue.add(tv)
          case _                                                          => //
        }
    }
  }
}
