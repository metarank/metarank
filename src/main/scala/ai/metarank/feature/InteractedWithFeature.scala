package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, ItemEvent, ItemRelevancy}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, UserScope}
import ai.metarank.model.FieldId.ItemFieldId
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, Field, FieldId, FieldName, MValue}
import ai.metarank.model.Identifier._
import ai.metarank.util.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.{
  BoundedListValue,
  FeatureConfig,
  FeatureValue,
  Key,
  SString,
  SStringList,
  ScalarValue,
  Write
}
import io.findify.featury.model.FeatureConfig.{BoundedListConfig, ScalarConfig}
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.Write.{Append, Put}

import scala.concurrent.duration.FiniteDuration

case class InteractedWithFeature(schema: InteractedWithSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  // stores last interactions of customer
  val lastValues = BoundedListConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name + s"_last"),
    count = schema.count.getOrElse(10),
    duration = schema.duration.getOrElse(24.hours),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val itemValues = ScalarConfig(
    scope = ItemScope.scope,
    name = FeatureName(schema.name + s"_field"),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(lastValues, itemValues)

  override def fields: List[FieldName] = List(schema.field)

  override def writes(event: Event, fields: FieldStore): Traversable[Write] =
    event match {
      case item: ItemEvent =>
        for {
          field <- item.fieldsMap.get(schema.field.field).toTraversable
          string <- field match {
            case Field.StringField(_, value)     => List(value)
            case Field.StringListField(_, value) => value
            case _                               => Nil
          }
        } yield {
          Put(
            key = Key(Tag(ItemScope.scope, item.item.value), itemValues.name, Tenant(event.tenant)),
            ts = event.timestamp,
            value = SString(string)
          )
        }
      case int: InteractionEvent if int.`type` == schema.interaction =>
        for {
          key <- schema.scope.keys(int, lastValues.name)
          field <- schema.field.event match {
            case EventType.Item =>
              fields.get(ItemFieldId(Tenant(event.tenant), int.item, schema.field.field)).toTraversable
            case _ => Traversable.empty
          }
          string <- field match {
            case Field.StringField(_, value)     => List(value)
            case Field.StringListField(_, value) => value
            case _                               => Nil
          }
        } yield {
          Append(key, SString(string), int.timestamp)
        }
      case _ => Traversable.empty
    }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      visitorKey      <- schema.scope.keys(request, lastValues.name).headOption
      interactedValue <- features.get(visitorKey)
      interactedList  <- interactedValue.cast[BoundedListValue]
      itemKey = Key(Tag(ItemScope.scope, id.id.value), itemValues.name, Tenant(request.tenant))
      itemFieldValue <- features.get(itemKey).flatMap(_.cast[ScalarValue])
    } yield {
      val interactedValues = interactedList.values.map(_.value).collect { case SString(value) => value }
      val itemValues = itemFieldValue.value match {
        case SString(value)      => List(value)
        case SStringList(values) => values
        case _                   => Nil
      }
      val counts = interactedValues.groupBy(identity).map { case (k, v) => k -> v.size }
      val value  = itemValues.foldLeft(0)((acc, next) => acc + counts.getOrElse(next, 0))
      SingleValue(schema.name, value)
    }
    result.getOrElse(SingleValue(schema.name, 0))
  }

}

object InteractedWithFeature {
  import ai.metarank.util.DurationJson._
  case class InteractedWithSchema(
      name: String,
      interaction: String,
      field: FieldName,
      scope: FeatureScope,
      count: Option[Int],
      duration: Option[FiniteDuration],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val interWithDecoder: Decoder[InteractedWithSchema] =
    deriveDecoder[InteractedWithSchema]
      .ensure(onlyItem, "can only be applied to item fields")
      .ensure(onlyUserSession, "can only be scoped to user/session")

  def onlyItem(schema: InteractedWithSchema) = schema.field.event match {
    case EventType.Item => true
    case _              => false
  }

  def onlyUserSession(schema: InteractedWithSchema) = schema.scope match {
    case FeatureScope.TenantScope  => false
    case FeatureScope.ItemScope    => false
    case FeatureScope.UserScope    => true
    case FeatureScope.SessionScope => true
  }
}
