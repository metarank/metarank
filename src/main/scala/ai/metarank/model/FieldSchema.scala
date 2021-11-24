package ai.metarank.model

sealed trait FieldSchema {
  def name: String
  def required: Boolean
  def source: FeatureSource
}

object FieldSchema {
  def unapply(e: FieldSchema): Option[(String, Boolean, FeatureSource)] = ???
  case class BooleanFieldSchema(name: String, source: FeatureSource, required: Boolean = false)    extends FieldSchema
  case class NumberFieldSchema(name: String, source: FeatureSource, required: Boolean = false)     extends FieldSchema
  case class StringFieldSchema(name: String, source: FeatureSource, required: Boolean = false)     extends FieldSchema
  case class NumberListFieldSchema(name: String, source: FeatureSource, required: Boolean = false) extends FieldSchema
  case class StringListFieldSchema(name: String, source: FeatureSource, required: Boolean = false) extends FieldSchema
  case class IPAddressFieldSchema(name: String, source: FeatureSource, required: Boolean = false)  extends FieldSchema
  case class UAFieldSchema(name: String, source: FeatureSource, required: Boolean = false)         extends FieldSchema
  case class RefererFieldSchema(name: String, source: FeatureSource, required: Boolean = false)    extends FieldSchema
}
