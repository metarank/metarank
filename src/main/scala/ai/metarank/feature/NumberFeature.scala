package ai.metarank.feature

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, FieldSchema, MValue}
import ai.metarank.model.Field.NumberField
import ai.metarank.model.FieldSchema.NumberFieldSchema
import ai.metarank.model.MValue.SingleValue
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, ScalarValue}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import shapeless.syntax.typeable._

import scala.concurrent.duration._

case class NumberFeature(schema: NumberFeatureSchema) extends MFeature {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = Scope(schema.scope.value),
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def fields: List[FieldSchema] = List(NumberFieldSchema(schema.source.field, schema.source))

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event): Iterable[Put] = for {
    key         <- keyOf(event)
    field       <- event.fields.find(_.name == schema.source.field)
    numberField <- field.cast[NumberField]
  } yield {
    Put(key, event.timestamp, SDouble(numberField.value))
  }

  override def keys(request: Event.RankingEvent): Traversable[Key] =
    request.items.map(item => Key(conf, Tenant(request.tenant), item.id.value))

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue =
    state.get(Key(conf, Tenant(request.tenant), id)) match {
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

  implicit val nfDecoder: Decoder[NumberFeatureSchema] = deriveDecoder
}
