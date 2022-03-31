package ai.metarank.flow

import ai.metarank.mode.StateTtl
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.{Event, EventId}
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor, ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

case class ImpressionInjectFunction(tpe: String, ttl: FiniteDuration)(implicit
    ri: TypeInformation[RankingEvent],
    ii: TypeInformation[InteractionEvent]
) extends KeyedProcessFunction[EventId, FeedbackEvent, Event]
    with CheckpointedFunction {

  @transient var ranking: ValueState[RankingEvent]         = _
  @transient var interactions: ListState[InteractionEvent] = _

  override def initializeState(context: FunctionInitializationContext): Unit = {
    val rankDesc = new ValueStateDescriptor("ranking", ri)
    rankDesc.enableTimeToLive(StateTtl(ttl))
    ranking = context.getKeyedStateStore.getState(rankDesc)
    val intDesc = new ListStateDescriptor("clicks", ii)
    intDesc.enableTimeToLive(StateTtl(ttl))
    interactions = context.getKeyedStateStore.getListState(intDesc)
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {}

  override def processElement(
      value: FeedbackEvent,
      ctx: KeyedProcessFunction[EventId, FeedbackEvent, Event]#Context,
      out: Collector[Event]
  ): Unit = value match {
    case i: InteractionEvent =>
      interactions.add(i)
    case r: RankingEvent =>
      ranking.update(r)
      ctx.timerService().registerEventTimeTimer(ctx.timestamp() + 1000L * 60 * 30)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[EventId, FeedbackEvent, Event]#OnTimerContext,
      out: Collector[Event]
  ): Unit = for {
    r      <- Option(ranking.value())
    clicks <- Option(interactions.get()).map(_.asScala.toList)
    ranks = r.items.toList.map(_.id).zipWithIndex.toMap
    (maxClick, maxClickRank) <- clicks.flatMap(c => ranks.get(c.item).map(rank => c -> rank)).sortBy(-_._2).headOption
    item                     <- r.items.take(maxClickRank + 1)
  } {
    val impression = InteractionEvent(
      id = EventId(UUID.randomUUID().toString),
      item = item.id,
      timestamp = maxClick.timestamp,
      ranking = r.id,
      user = r.user,
      session = r.session,
      `type` = tpe,
      fields = Nil,
      tenant = r.tenant
    )
    out.collect(impression)
  }

}
