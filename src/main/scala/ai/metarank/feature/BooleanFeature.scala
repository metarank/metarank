package ai.metarank.feature

import ai.metarank.model.Event.MetadataEvent
import ai.metarank.model.FeatureSchema.BooleanFeatureSchema
import ai.metarank.model.Field.{BooleanField, NumberField}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Event, MValue}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SBoolean, SDouble, ScalarValue}

import scala.concurrent.duration._
import shapeless.syntax.typeable._

case class BooleanFeature(schema: BooleanFeatureSchema) extends MFeature {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = Scope(schema.source.asString),
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event): Iterable[Put] = for {
    meta       <- event.cast[MetadataEvent]
    field      <- meta.fields.find(_.name == schema.field)
    fieldValue <- field.cast[BooleanField]
  } yield {
    Put(
      Key(conf, tenant(event), meta.item.value),
      meta.timestamp,
      SBoolean(fieldValue.value)
    )
  }

  override def keys(request: Event.RankingEvent): Traversable[Key] =
    request.items.map(item => Key(conf, tenant(request), item.id.value))

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue =
    state.get(Key(conf, tenant(request), id)) match {
      case Some(ScalarValue(_, _, SBoolean(value))) => SingleValue(schema.name, if (value) 1 else 0)
      case _                                        => SingleValue(schema.name, 0.0)
    }

}
