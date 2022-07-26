package ai.metarank.flow

import ai.metarank.FeatureMapping
import ai.metarank.util.persistence.field.{FieldStore, FlinkFieldStore, RedisFieldStore}
import ai.metarank.model.{Event, Field, FieldId, FieldUpdate}
import ai.metarank.util.Logging
import ai.metarank.util.persistence.field.FieldStore.FieldStoreFactory
import ai.metarank.util.persistence.field.FlinkFieldStore.FlinkFieldStoreFactory
import ai.metarank.util.persistence.field.RedisFieldStore.RedisFieldStoreFactory
import io.findify.featury.flink.util.InitContext.DataSetContext
import io.findify.featury.model.Key.Tenant
import io.findify.featury.model.{Schema, State, Write}
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.{BroadcastProcessFunction, KeyedCoProcessFunction}
import org.apache.flink.util.Collector

case class EventProcessFunction(
    mapping: FeatureMapping,
    persistenceFactory: FieldStoreFactory
) extends KeyedCoProcessFunction[Tenant, Event, FieldUpdate, Write]
    with Logging {

  @transient var fields: FieldStore = _

  override def open(parameters: Configuration): Unit = {
    fields = persistenceFactory match {
      case f: FlinkFieldStoreFactory => f.create("fields", DataSetContext(getRuntimeContext))
      case f: RedisFieldStoreFactory => f.create()
    }
  }

  override def processElement1(
      value: Event,
      ctx: KeyedCoProcessFunction[Tenant, Event, FieldUpdate, Write]#Context,
      out: Collector[Write]
  ): Unit = {
    val writes = mapping.features.flatMap(_.writes(value, fields))
    // logger.debug(s"feedback event: $value, expanded to ${writes.size} writes: $writes")
    writes.foreach(w => out.collect(w))

  }

  override def processElement2(
      value: FieldUpdate,
      ctx: KeyedCoProcessFunction[Tenant, Event, FieldUpdate, Write]#Context,
      out: Collector[Write]
  ): Unit = {
    fields.put(value.id, value.value)
  }
}
