package ai.metarank.flow

import ai.metarank.mode.StateTtl
import ai.metarank.model.{Field, ItemId, UserId}
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor, ValueState}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.state.FunctionInitializationContext

import scala.concurrent.duration.FiniteDuration

sealed trait FieldStore[T] {
  def get(id: T): Option[List[Field]]
}

object FieldStore {
  case class FlinkFieldStore[T](state: MapState[T, List[Field]]) extends FieldStore[T] {
    override def get(id: T): Option[List[Field]] = Option(state.get(id))
  }

  case class EmptyFieldStore[T]() extends FieldStore[T] {
    override def get(id: T): Option[List[Field]] = None
  }

  case class MapFieldStore[T](values: Map[T, List[Field]]) extends FieldStore[T] {
    override def get(id: T): Option[List[Field]] = values.get(id)
  }

  def empty[T] = EmptyFieldStore[T]()

  def map[T](values: Map[T, List[Field]]) = MapFieldStore(values)

  def flink[T](name: String, ctx: FunctionInitializationContext, ttl: FiniteDuration)(implicit
      ki: TypeInformation[T],
      vi: TypeInformation[List[Field]]
  ) = {
    val desc = new MapStateDescriptor[T, List[Field]](name, ki, vi)
    desc.enableTimeToLive(StateTtl(ttl))
    new FlinkFieldStore[T](ctx.getKeyedStateStore.getMapState(desc))
  }

}
