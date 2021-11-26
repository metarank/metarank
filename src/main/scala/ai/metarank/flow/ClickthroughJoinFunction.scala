package ai.metarank.flow

import ai.metarank.mode.StateTtl
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent}
import ai.metarank.model.{Clickthrough, Event, EventId, SessionId}
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ClickthroughJoinFunction(ttl: FiniteDuration = 1.hour)(implicit ti: TypeInformation[Event])
    extends KeyedProcessFunction[EventId, FeedbackEvent, Clickthrough]
    with CheckpointedFunction {

  var eventList: ListState[Event] = _

  override def initializeState(context: FunctionInitializationContext): Unit = {
    val desc = new ListStateDescriptor[Event]("events", ti)
    desc.enableTimeToLive(StateTtl(ttl))
    eventList = context.getKeyedStateStore.getListState(desc)
  }

  override def snapshotState(context: FunctionSnapshotContext): Unit = {}

  override def processElement(
      value: FeedbackEvent,
      ctx: KeyedProcessFunction[EventId, FeedbackEvent, Clickthrough]#Context,
      out: Collector[Clickthrough]
  ): Unit = value match {
    case rank: RankingEvent =>
      eventList.add(rank)
      ctx.timerService().registerEventTimeTimer(ctx.timestamp() + 30 * 60 * 1000)
    case int: InteractionEvent =>
      eventList.add(int)
  }

  override def onTimer(
      timestamp: Long,
      ctx: KeyedProcessFunction[EventId, FeedbackEvent, Clickthrough]#OnTimerContext,
      out: Collector[Clickthrough]
  ): Unit = {
    for {
      events  <- Option(eventList.get().asScala.toList)
      ranking <- events.collectFirst { case e: RankingEvent => e }
    } {
      val ints = events.collect { case e: InteractionEvent => e }
      out.collect(Clickthrough(ranking, ints))
    }
  }
}
