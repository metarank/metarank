package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.util.persistence.field.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import ai.metarank.model.Identifier._
import ai.metarank.model.Field.NumberField
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, ScalarValue}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import shapeless.syntax.typeable._

import scala.concurrent.duration._

case class NumberFeature(schema: NumberFeatureSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def fields: List[FieldName] = List(schema.source)

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, fields: FieldStore): Iterable[Put] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.source.field)
    numberField <- field match {
      case n: NumberField => Some(n)
      case other =>
        logger.warn(s"field extractor ${schema.name} expects a boolean, but got $other in event $event")
        None
    }
  } yield {
    Put(key, event.timestamp, SDouble(numberField.value))
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue =
    features.get(Key(conf, Tenant(request.tenant), id.id.value)) match {
      case Some(ScalarValue(_, _, SDouble(value))) => SingleValue(schema.name, value)
      case _                                       => SingleValue(schema.name, 0.0)
    }

}

object NumberFeature {
  import ai.metarank.util.DurationJson._
  case class NumberFeatureSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val nfDecoder: Decoder[NumberFeatureSchema] =
    deriveDecoder[NumberFeatureSchema].withErrorMessage("cannot parse a feature definition of type 'number'")
}
