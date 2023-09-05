package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.ItemAgeFeature.ItemAgeSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.{RankItem, eventCodec}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.NumberField
import ai.metarank.model.FieldName.EventType._
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, Field, FieldName, Key, MValue, ScopeType, Timestamp}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDouble
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class ItemAgeFeature(schema: ItemAgeSchema) extends ItemFeature with Logging {
  override def dim = SingleDim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO {
    for {
      key <- writeKey(event, conf)
      field <- schema.source.field match {
        case "timestamp" => Some(NumberField("timestamp", event.timestamp.ts / 1000.0))
        case _           => event.fields.find(_.name == schema.source.field)
      }

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
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)
  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue =
    features.get(Key(ItemScope(id.id), conf.name)) match {
      case Some(ScalarValue(_, _, SDouble(value), _)) =>
        val updatedAt = Timestamp(math.round(value * 1000))
        SingleValue(schema.name, updatedAt.diff(request.timestamp).toSeconds.toDouble)
      case _ => SingleValue.missing(schema.name)
    }

}

object ItemAgeFeature {
  import ai.metarank.util.DurationJson._
  case class ItemAgeSchema(
      name: FeatureName,
      source: FieldName,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = ItemScopeType

    override def create(): IO[BaseFeature] = IO.pure(ItemAgeFeature(this))
  }

  implicit val itemAgeDecoder: Decoder[ItemAgeSchema] =
    deriveDecoder[ItemAgeSchema]
      .ensure(_.source.event == Item, "can only work with fields from metadata events")
      .withErrorMessage("cannot parse a feature definition of type 'item_age'")

  implicit val itemAgeEncoder: Encoder[ItemAgeSchema] = deriveEncoder
}
