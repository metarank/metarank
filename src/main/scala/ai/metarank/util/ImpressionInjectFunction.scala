package ai.metarank.util

import ai.metarank.model.{Event, EventId, ItemId}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, MetadataEvent, RankingEvent}
import io.findify.featury.model.Key.Tenant
import org.apache.flink.api.common.state.{
  ListState,
  ListStateDescriptor,
  StateTtlConfig,
  ValueState,
  ValueStateDescriptor
}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.DataStream
import org.apache.flink.util.Collector
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow

import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

class ImpressionInjectFunction(tpe: String, ttl: FiniteDuration)(implicit
    ri: TypeInformation[RankingEvent],
    ii: TypeInformation[InteractionEvent]
) extends KeyedProcessFunction[EventId, FeedbackEvent, InteractionEvent]
    with CheckpointedFunction {

  @transient var ranking: ValueState[RankingEvent]         = _
  @transient var interactions: ListState[InteractionEvent] = _

  override def initializeState(context: FunctionInitializationContext): Unit = {
    val ttlconf = StateTtlConfig
      .newBuilder(org.apache.flink.api.common.time.Time.seconds(ttl.toSeconds))
      .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
      .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
      .cleanupFullSnapshot()
      .build()
    val rankDesc = new ValueStateDescriptor("ranking", ri)
    rankDesc.enableTimeToLive(ttlconf)
    ranking = context.getKeyedStateStore.getState(rankDesc)
    val intDesc = new ListStateDescriptor("clicks", ii)
    intDesc.enableTimeToLive(ttlconf)
    interactions = context.getKeyedStateStore.getListState(intDesc)
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {}

  override def processElement(
      value: FeedbackEvent,
      ctx: KeyedProcessFunction[EventId, FeedbackEvent, InteractionEvent]#Context,
      out: Collector[InteractionEvent]
  ): Unit = value match {
    case i: InteractionEvent =>
      interactions.add(i)
    case r: RankingEvent =>
      ranking.update(r)
      ctx.timerService().registerEventTimeTimer(ctx.timestamp() + 1000L * 60 * 30)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[EventId, FeedbackEvent, InteractionEvent]#OnTimerContext,
      out: Collector[InteractionEvent]
  ): Unit = for {
    r      <- Option(ranking.value())
    clicks <- Option(interactions.get()).map(_.asScala.toList)
    ranks = r.items.map(_.id).zipWithIndex.toMap
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
