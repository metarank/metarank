package ai.metarank.mode.bootstrap

import ai.metarank.model.{Field, FieldId, FieldUpdate}
import ai.metarank.util.persistence.field.{FieldStore, FlinkFieldStore, RedisFieldStore}
import ai.metarank.util.persistence.field.FieldStore.FieldStoreFactory
import io.findify.featury.flink.util.InitContext.DataSetContext
import io.findify.featury.model.Key.Tenant
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.state.api.functions.{BroadcastStateBootstrapFunction, KeyedStateBootstrapFunction}

case class FieldValueBootstrapFunction(persistenceFactory: FieldStoreFactory)
    extends KeyedStateBootstrapFunction[Tenant, FieldUpdate] {

  @transient var fields: FieldStore = _

  override def open(parameters: Configuration): Unit = {
    fields = persistenceFactory match {
      case f: FlinkFieldStore.FlinkFieldStoreFactory => f.create("fields", DataSetContext(getRuntimeContext))
      case f: RedisFieldStore.RedisFieldStoreFactory => f.create()
    }
  }

  override def processElement(value: FieldUpdate, ctx: KeyedStateBootstrapFunction[Tenant, FieldUpdate]#Context): Unit =
    fields.put(value.id, value.value)

}
