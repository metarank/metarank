package ai.metarank.feature

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.util.persistence.field.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Field.{BooleanField, NumberField}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import ai.metarank.util.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SBoolean, ScalarValue}
import ai.metarank.model.Identifier._

import scala.concurrent.duration._
import shapeless.syntax.typeable._

case class BooleanFeature(schema: BooleanFeatureSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(schema.source)

  override def writes(event: Event, fields: FieldStore): Iterable[Put] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.source.field)
    fieldValue <- field match {
      case b: BooleanField => Some(b)
      case other =>
        logger.warn(s"field extractor ${schema.name} expects a boolean, but got $other in event $event")
        None
    }
  } yield {
    Put(key, event.timestamp, SBoolean(fieldValue.value))
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue =
    features.get(Key(conf, Tenant(request.tenant), id.id.value)) match {
      case Some(ScalarValue(_, _, SBoolean(value))) => SingleValue(schema.name, if (value) 1 else 0)
      case _                                        => SingleValue(schema.name, 0.0)
    }

}

object BooleanFeature {
  import ai.metarank.util.DurationJson._
  case class BooleanFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val boolSchemaDecoder: Decoder[BooleanFeatureSchema] =
    deriveDecoder[BooleanFeatureSchema].withErrorMessage("cannot parse a feature definition of type 'boolean'")
}
