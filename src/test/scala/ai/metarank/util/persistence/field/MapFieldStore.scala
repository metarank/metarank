package ai.metarank.util.persistence.field

import ai.metarank.model.{Field, FieldId}

case class MapFieldStore(cache: Map[FieldId, Field] = Map.empty) extends FieldStore {
  override def get(id: FieldId): Option[Field]      = cache.get(id)
  override def put(id: FieldId, value: Field): Unit = ??? // cache.put(id, value)
}
