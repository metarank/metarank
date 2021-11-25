package ai.metarank.mode

import ai.metarank.model.{Clickthrough, Event, SessionId}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector

class ClickthroughJoinFunction extends KeyedProcessFunction[SessionId, Event, Clickthrough] {
  override def processElement(
      value: Event,
      ctx: KeyedProcessFunction[SessionId, Event, Clickthrough]#Context,
      out: Collector[Clickthrough]
  ): Unit = ???
}
