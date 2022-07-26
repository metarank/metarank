package ai.metarank.util.persistence.field

import ai.metarank.model.{Field, FieldId}

trait FieldStore {
  def put(id: FieldId, value: Field): Unit
  def get(id: FieldId): Option[Field]
}

object FieldStore {
  trait FieldStoreFactory
}
