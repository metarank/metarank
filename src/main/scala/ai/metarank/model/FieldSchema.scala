package ai.metarank.model

sealed trait FieldSchema {
  def required: Boolean
  def source: FieldName
}

object FieldSchema {
  case class BooleanFieldSchema(source: FieldName, required: Boolean = false)    extends FieldSchema
  case class NumberFieldSchema(source: FieldName, required: Boolean = false)     extends FieldSchema
  case class StringFieldSchema(source: FieldName, required: Boolean = false)     extends FieldSchema
  case class NumberListFieldSchema(source: FieldName, required: Boolean = false) extends FieldSchema
  case class StringListFieldSchema(source: FieldName, required: Boolean = false) extends FieldSchema
  case class IPAddressFieldSchema(source: FieldName, required: Boolean = false)  extends FieldSchema
  case class UAFieldSchema(source: FieldName, required: Boolean = false)         extends FieldSchema
  case class RefererFieldSchema(source: FieldName, required: Boolean = false)    extends FieldSchema
}
