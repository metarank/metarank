package ai.metarank.feature

import ai.metarank.feature.MetaFeature.StatelessFeature
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, ItemId, MValue}
import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, SString, SStringList, ScalarValue}

import scala.concurrent.duration._

case class StringFeature(schema: StringFeatureSchema) extends StatelessFeature {
  val possibleValues    = schema.values.toList
  val names             = possibleValues.map(value => s"${schema.name}_$value")
  override def dim: Int = schema.values.size

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(StringFieldSchema(schema.source))

  override def writes(event: Event): Iterable[Put] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.source.field)
    fieldValue <- field match {
      case StringField(_, value)     => Some(SStringList(List(value)))
      case StringListField(_, value) => Some(SStringList(value))
      case _                         => None
    }
  } yield {
    Put(key, event.timestamp, fieldValue)
  }

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemId
  ): MValue =
    state.get(Key(conf, Tenant(request.tenant), id.value)) match {
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

object StringFeature {
  import ai.metarank.util.DurationJson._
  case class StringFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      values: NonEmptyList[String],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val stringSchemaDecoder: Decoder[StringFeatureSchema] = deriveDecoder
}
