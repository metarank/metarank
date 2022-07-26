package ai.metarank.util.persistence.field

import ai.metarank.model.{Field, FieldId}
import ai.metarank.util.Logging
import ai.metarank.util.persistence.field.FieldStore.FieldStoreFactory
import io.findify.featury.flink.util.InitContext
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation

case class FlinkFieldStore(map: MapState[FieldId, Field]) extends FieldStore with Logging {
  override def get(id: FieldId): Option[Field] = Option(map.get(id))

  override def put(id: FieldId, value: Field): Unit = map.put(id, value)
}

object FlinkFieldStore {
  def apply(
      name: String,
      ctx: InitContext
  )(ki: TypeInformation[FieldId], vi: TypeInformation[Field]): FlinkFieldStore = {
    val desc = new MapStateDescriptor[FieldId, Field](name, ki, vi)
    new FlinkFieldStore(ctx.getMapState(desc))
  }

  case class FlinkFieldStoreFactory()(implicit ki: TypeInformation[FieldId], vi: TypeInformation[Field])
      extends FieldStoreFactory {
    def create(
        name: String,
        ctx: InitContext
    ): FlinkFieldStore = {
      FlinkFieldStore.apply(name, ctx)(ki, vi)
    }
  }
}
