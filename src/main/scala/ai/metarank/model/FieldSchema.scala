package ai.metarank.model

sealed trait FieldSchema {
  def name: String
  def required: Boolean
  def source: FieldName
}

object FieldSchema {
  def unapply(e: FieldSchema): Option[(String, Boolean, FieldName)] = Some((e.name, e.required, e.source))
  case class BooleanFieldSchema(name: String, source: FieldName, required: Boolean = false)    extends FieldSchema
  case class NumberFieldSchema(name: String, source: FieldName, required: Boolean = false)     extends FieldSchema
  case class StringFieldSchema(name: String, source: FieldName, required: Boolean = false)     extends FieldSchema
  case class NumberListFieldSchema(name: String, source: FieldName, required: Boolean = false) extends FieldSchema
  case class StringListFieldSchema(name: String, source: FieldName, required: Boolean = false) extends FieldSchema
  case class IPAddressFieldSchema(name: String, source: FieldName, required: Boolean = false)  extends FieldSchema
  case class UAFieldSchema(name: String, source: FieldName, required: Boolean = false)         extends FieldSchema
  case class RefererFieldSchema(name: String, source: FieldName, required: Boolean = false)    extends FieldSchema
}
