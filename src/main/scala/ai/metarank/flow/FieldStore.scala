package ai.metarank.flow

import ai.metarank.model.{Field, FieldId}
import org.apache.flink.api.common.state.{MapState, ReadOnlyBroadcastState}

sealed trait FieldStore {
  def get(id: FieldId): Option[Field]
}

object FieldStore {
  case class FlinkFieldStore(state: ReadOnlyBroadcastState[FieldId, Field]) extends FieldStore {
    override def get(id: FieldId): Option[Field] = Option(state.get(id))
  }

  case object EmptyFieldStore extends FieldStore {
    override def get(id: FieldId): Option[Field] = None
  }

  case class MapFieldStore(values: Map[FieldId, Field]) extends FieldStore {
    override def get(id: FieldId): Option[Field] = values.get(id)
  }

  def empty = EmptyFieldStore

  def map(values: Map[FieldId, Field]) = MapFieldStore(values)

  def flink(state: ReadOnlyBroadcastState[FieldId, Field]) = FlinkFieldStore(state)

}
