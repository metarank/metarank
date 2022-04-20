package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.flow.FieldStore.FlinkFieldStore
import ai.metarank.model.{Event, Field, FieldId, FieldUpdate}
import ai.metarank.util.Logging
import io.findify.featury.model.{Schema, State, Write}
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction
import org.apache.flink.util.Collector

case class EventProcessFunction(desc: MapStateDescriptor[FieldId, Field], mapping: FeatureMapping)
    extends BroadcastProcessFunction[Event, FieldUpdate, Write]
    with Logging {
  override def processBroadcastElement(
      value: FieldUpdate,
      ctx: BroadcastProcessFunction[Event, FieldUpdate, Write]#Context,
      out: Collector[Write]
  ): Unit = {
    logger.debug(s"field update: ${value.id}=${value.value}")
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
    logger.debug(s"feedback event: $value, expanded to ${writes.size} writes: $writes")
    writes.foreach(w => out.collect(w))
  }
}
