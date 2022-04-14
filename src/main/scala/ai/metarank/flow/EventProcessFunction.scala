package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.flow.FieldStore.FlinkFieldStore
import ai.metarank.model.{Event, Field, FieldId, FieldUpdate}
import io.findify.featury.model.{Schema, State, Write}
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.util.Collector

case class EventProcessFunction(desc: MapStateDescriptor[FieldId, Field], mapping: FeatureMapping)
    extends BroadcastProcessFunction[Event, FieldUpdate, Write] {
  override def processBroadcastElement(
      value: FieldUpdate,
      ctx: BroadcastProcessFunction[Event, FieldUpdate, Write]#Context,
      out: Collector[Write]
  ): Unit = {
    val state = ctx.getBroadcastState(desc)
    state.put(value.id, value.value)
  }

  override def processElement(
      value: Event,
      ctx: BroadcastProcessFunction[Event, FieldUpdate, Write]#ReadOnlyContext,
      out: Collector[Write]
  ): Unit = {
    val state  = FlinkFieldStore(ctx.getBroadcastState(desc))
    val writes = mapping.features.flatMap(_.writes(value, state))
    writes.foreach(w => out.collect(w))
  }
}
