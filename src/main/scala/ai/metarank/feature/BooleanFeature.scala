package ai.metarank.feature

import ai.metarank.feature.BooleanFeature.BooleanFeatureSchema
import ai.metarank.model.Field.{BooleanField, NumberField}
import ai.metarank.model.FieldSchema.BooleanFieldSchema
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tenant}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SBoolean, SDouble, ScalarValue}

import scala.concurrent.duration._
import shapeless.syntax.typeable._

case class BooleanFeature(schema: BooleanFeatureSchema) extends MFeature {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = Scope(schema.scope.value),
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(
    BooleanFieldSchema(schema.source.field, source = schema.source)
  )

  override def writes(event: Event): Iterable[Put] = for {
    key        <- keyOf(event)
    field      <- event.fields.find(_.name == schema.source.field)
    fieldValue <- field.cast[BooleanField]
  } yield {
    Put(key, event.timestamp, SBoolean(fieldValue.value))
  }

  override def keys(request: Event.RankingEvent): Traversable[Key] =
    request.items.map(item => Key(conf, Tenant(request.tenant), item.id.value))

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue =
    state.get(Key(conf, Tenant(request.tenant), id)) match {
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

  implicit val boolSchemaDecoder: Decoder[BooleanFeatureSchema] = deriveDecoder
}
