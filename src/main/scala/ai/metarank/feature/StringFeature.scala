package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemStatelessFeature
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, ItemId, MValue}
import ai.metarank.util.{Logging, OneHotEncoder}
import cats.data.NonEmptyList
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, SString, SStringList, ScalarValue}

import scala.concurrent.duration._

case class StringFeature(schema: StringFeatureSchema) extends ItemStatelessFeature with Logging {
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
      case other =>
        logger.warn(s"field extractor ${schema.name} expects a string or string[], but got $other in event $event")
        None
    }
  } yield {
    Put(key, event.timestamp, fieldValue)
  }

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key   <- keyOf(request, Some(id.id))
      value <- state.get(key)
    } yield {
      value
    }
    result match {
      case Some(ScalarValue(_, _, SStringList(value))) =>
        VectorValue(names, OneHotEncoder.fromValues(value, possibleValues, dim), dim)
      case _ => VectorValue(names, OneHotEncoder.fromValues(Nil, possibleValues, dim), dim)
    }
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
