package ai.metarank.feature

import ai.metarank.model.Event.MetadataEvent
import ai.metarank.model.FeatureSchema.WordCountSchema
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.{Event, MValue}
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope}
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, SString, ScalarValue}

import scala.concurrent.duration._
import shapeless.syntax.typeable._

case class WordCountFeature(schema: WordCountSchema) extends MFeature {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = Scope(schema.source.asString),
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def fields                      = List(StringFieldSchema(schema.field, schema.source))
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event): Iterable[Put] = for {
    meta       <- event.cast[MetadataEvent]
    field      <- meta.fields.find(_.name == schema.field)
    fieldValue <- field.cast[StringField]
  } yield {
    Put(
      Key(conf, tenant(event), meta.item.value),
      meta.timestamp,
      SDouble(tokenCount(fieldValue.value))
    )
  }

  override def keys(request: Event.RankingEvent): Traversable[Key] =
    request.items.map(item => Key(conf, tenant(request), item.id.value))

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: String): MValue =
    state.get(Key(conf, tenant(request), id)) match {
      case Some(ScalarValue(_, _, SDouble(value))) => SingleValue(schema.name, value)
      case _                                       => SingleValue(schema.name, 0)
    }

  val whitespacePattern = "\\s+".r
  def tokenCount(string: String): Int = {
    whitespacePattern.split(string).length
  }
}
