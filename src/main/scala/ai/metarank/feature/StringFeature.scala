package ai.metarank.feature

import ai.metarank.model.Event.MetadataEvent
import ai.metarank.model.FeatureSchema.StringFeatureSchema
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.{Event, MValue}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, SString, SStringList, ScalarValue}
import shapeless.syntax.typeable._

import scala.concurrent.duration._

case class StringFeature(schema: StringFeatureSchema) extends MFeature {
  val possibleValues    = schema.values.toList
  val names             = possibleValues.map(value => s"${schema.name}_$value")
  override def dim: Int = schema.values.size

  private val conf = ScalarConfig(
    scope = Scope(schema.source.asString),
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(StringFieldSchema(schema.field, schema.source))

  override def writes(event: Event): Iterable[Put] = for {
    meta  <- event.cast[MetadataEvent]
    field <- meta.fields.find(_.name == schema.field)
    fieldValue <- field match {
      case StringField(_, value)     => Some(SStringList(List(value)))
      case StringListField(_, value) => Some(SStringList(value))
      case _                         => None
    }
  } yield {
    Put(
      Key(conf, tenant(event), meta.item.value),
      meta.timestamp,
      fieldValue
    )
  }

  override def keys(request: Event.RankingEvent): Traversable[Key] =
    request.items.map(item => Key(conf, tenant(request), item.id.value))

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue =
    state.get(Key(conf, tenant(request), id)) match {
      case Some(ScalarValue(_, _, SStringList(value))) => VectorValue(names, oneHotEncode(value), dim)
      case _                                           => VectorValue(names, oneHotEncode(Nil), dim)
    }

  def oneHotEncode(values: Seq[String]): Array[Double] = {
    val result = new Array[Double](dim)
    for {
      value <- values
    } {
      val index = possibleValues.indexOf(value)
      if (index >= 0) {
        result(index) = 1.0
      }
    }
    result
  }

}
