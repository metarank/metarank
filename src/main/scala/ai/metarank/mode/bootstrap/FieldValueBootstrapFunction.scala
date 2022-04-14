package ai.metarank.mode.bootstrap

import ai.metarank.model.{Field, FieldId, FieldUpdate}
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.state.api.functions.BroadcastStateBootstrapFunction

case class FieldValueBootstrapFunction(desc: MapStateDescriptor[FieldId, Field])
    extends BroadcastStateBootstrapFunction[FieldUpdate] {

  override def processElement(value: FieldUpdate, ctx: BroadcastStateBootstrapFunction.Context): Unit = {
    val state = ctx.getBroadcastState(desc)
    state.put(value.id, value.value)
  }
}
