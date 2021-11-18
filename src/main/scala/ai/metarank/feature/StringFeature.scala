package ai.metarank.feature

import ai.metarank.model.Event.MetadataEvent
import ai.metarank.model.FeatureSchema.StringFeatureSchema
import ai.metarank.model.Field.{NumberField, StringField}
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.{Event, MValue}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, SString, ScalarValue}
import shapeless.syntax.typeable._

import scala.concurrent.duration._

case class StringFeature(schema: StringFeatureSchema) extends MFeature {
  val possibleValues    = schema.values.toList
  override def dim: Int = schema.values.size

  private val conf = ScalarConfig(
    scope = Scope(schema.source),
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event): Iterable[Put] = for {
    meta       <- event.cast[MetadataEvent]
    field      <- meta.fields.find(_.name == schema.field)
    fieldValue <- field.cast[StringField]
  } yield {
    Put(
      Key(conf, tenant(event), meta.item.value),
      meta.timestamp,
      SString(fieldValue.value)
    )
  }

  override def keys(request: Event.ImpressionEvent): Traversable[Key] =
    request.items.map(item => Key(conf, tenant(request), item.id.value))

  override def value(request: Event.ImpressionEvent, state: Map[Key, FeatureValue], id: String): MValue =
    state.get(Key(conf, tenant(request), id)) match {
      case Some(ScalarValue(_, _, SString(value))) => VectorValue(schema.name, oneHotEncode(Some(value)), dim)
      case _                                       => VectorValue(schema.name, oneHotEncode(None), dim)
    }

  def oneHotEncode(value: Option[String]): Array[Double] = value match {
    case Some(str) =>
      val result = new Array[Double](dim)
      val index  = possibleValues.indexOf(str)
      if (index >= 0) {
        result(index) = 1.0
      }
      result
    case None =>
      new Array[Double](dim)
  }

}
