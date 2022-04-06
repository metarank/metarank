package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FieldName.Item
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, Field, FieldName, ItemId, MValue, UserId}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Tenant}
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SBoolean, SDouble, ScalarValue, Timestamp}
import io.findify.featury.model.Write.Put

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class ItemAgeFeature(schema: ItemAgeSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(schema.source)

  override def writes(event: Event, user: FieldStore[UserId], item: FieldStore[ItemId]): Iterable[Put] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.source.field)
    fieldValue <- field match {
      case Field.NumberField(_, value) => Some(value) // unix time
      case Field.StringField(_, value) =>
        Try(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)) match {
          case Failure(exIsoTime) =>
            Try(java.lang.Double.parseDouble(value)) match {
              case Failure(exNumber) =>
                logger.warn(s"${schema.name}: cannot parse ts '$value' as unixtime", exNumber)
                logger.warn(s"${schema.name}: cannot parse ts '$value' as ISO datetime", exIsoTime)
                None
              case Success(value) => Some(value)
            }
          case Success(value) => Some(value.toEpochSecond.toDouble)
        }
      case other =>
        logger.warn(s"${schema.name}: expected string|number field type, but got $other")
        None
    }
  } yield {
    Put(key, event.timestamp, SDouble(fieldValue))
  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue =
    features.get(Key(conf, Tenant(request.tenant), id.id.value)) match {
      case Some(ScalarValue(_, _, SDouble(value))) =>
        val updatedAt = Timestamp(math.round(value * 1000))
        SingleValue(schema.name, updatedAt.diff(request.timestamp).toSeconds)
      case _ => SingleValue(schema.name, 0.0)
    }

}

object ItemAgeFeature {
  import ai.metarank.util.DurationJson._
  case class ItemAgeSchema(
      name: String,
      source: FieldName,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = FeatureScope.ItemScope
  }

  implicit val itemAgeDecoder: Decoder[ItemAgeSchema] =
    deriveDecoder[ItemAgeSchema].ensure(_.source.event == Item, "can only work with fields from metadata events")

}
