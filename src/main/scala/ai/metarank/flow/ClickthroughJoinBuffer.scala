package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.config.CoreConfig.ClickthroughJoinConfig
import ai.metarank.flow.ClickthroughJoinBuffer.Node
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, ClickthroughValues, Event, ItemValue, Timestamp}
import ai.metarank.util.Logging
import cats.effect.IO
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ArrayBuffer

case class ClickthroughJoinBuffer(
    queue: util.ArrayDeque[Node],
    rankings: ConcurrentHashMap[String, Node],
    state: Persistence,
    mapping: FeatureMapping,
    conf: ClickthroughJoinConfig
) extends Logging {

  def process(event: Event): IO[List[Clickthrough]] = {
    event match {
      case e: RankingEvent =>
        handleRanking(e) *> flushQueue(event.timestamp)
      case e: InteractionEvent =>
        handleInteraction(e) *> flushQueue(event.timestamp)
      case _ =>
        flushQueue(event.timestamp)
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
    node = new Node(ctv)
    _ <- IO(queue.addLast(node))
    _ <- IO(rankings.put(ctv.ct.id.value, node))
  } yield {}

  def handleInteraction(event: InteractionEvent): IO[Unit] = for {
    nodeOption <- IO(event.ranking.flatMap(id => Option(rankings.get(id.value))))
    _ <- nodeOption match {
      case None => IO.unit
      case Some(node) =>
        IO {
          val updated = node.payload.copy(node.payload.ct.withInteraction(event.item, event.`type`))
          node.payload = updated
        }
    }
  } yield {}

  def flushQueue(now: Timestamp): IO[List[Clickthrough]] = for {
    expired <- IO(pollExpired(now))
    _       <- if (expired.nonEmpty) state.cts.put(expired) else IO.unit
  } yield {
    expired.map(_.ct)
  }

  def pollExpired(now: Timestamp) = {
    val buffer    = new ArrayBuffer[ClickthroughValues]()
    val threshold = now.minus(conf.maxSessionLength)
    while (
      (queue.size() > conf.maxParallelSessions) || (!queue.isEmpty && queue
        .peekFirst()
        .payload
        .ct
        .ts
        .isBeforeOrEquals(threshold))
    ) {
      val expired = queue.pollFirst()
      rankings.remove(expired.payload.ct.id.value)
      if (expired.payload.ct.interactions.nonEmpty) buffer.addOne(expired.payload)
    }
    buffer.toList
  }
}

object ClickthroughJoinBuffer extends Logging {
  class Node(var payload: ClickthroughValues)
  def apply(conf: ClickthroughJoinConfig, store: Persistence, mapping: FeatureMapping) = {
    val deque    = new util.ArrayDeque[Node](conf.maxParallelSessions)
    val rankings = new ConcurrentHashMap[String, Node](conf.maxParallelSessions)

    new ClickthroughJoinBuffer(
      state = store,
      queue = deque,
      mapping = mapping,
      rankings = rankings,
      conf = conf
    )
  }
}
