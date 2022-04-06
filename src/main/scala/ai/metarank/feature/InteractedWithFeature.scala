package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, ItemEvent, ItemRelevancy}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, UserScope}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, Field, FieldName, ItemId, MValue, UserId}
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
import io.findify.featury.model.Write.Put

import scala.concurrent.duration.FiniteDuration

case class InteractedWithFeature(schema: InteractedWithSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  // stores last interactions of customer
  val listConf = BoundedListConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name + s"_last"),
    count = schema.count.getOrElse(10),
    duration = schema.duration.getOrElse(24.hours),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val fieldConf = ScalarConfig(
    scope = ItemScope.scope,
    name = FeatureName(s"${schema.name}_field"),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(listConf, fieldConf)

  override def fields: List[FieldName] = List(schema.field)

  override def writes(event: Event, user: FieldStore[UserId], item: FieldStore[ItemId]): Traversable[Write] =
    event match {
      case meta: ItemEvent =>
        for {
          field <- meta.fields.find(_.name == schema.field.field).toTraversable
          key   <- ItemScope.keys(meta, fieldConf.name)
          value <- field match {
            case Field.StringField(_, value)      => Some(SString(value))
            case Field.StringListField(_, values) => Some(SStringList(values))
            case other =>
              logger.warn(
                s"field extractor ${schema.name} expects a string or string[], but got $other in event $event"
              )
              None
          }
        } yield {
          Put(
            key = key,
            ts = meta.timestamp,
            value = value
          )
        }
      case _ => Traversable.empty
    }

//  override def writes(event: Event, state: Map[Key, FeatureValue]): Traversable[Write] = {
//    event match {
//      case int: InteractionEvent if int.`type` == schema.interaction =>
//        for {
//          itemKey   <- ItemScope.keys(int, fieldConf.name)
//          itemValue <- state.get(itemKey).toTraversable
//          key       <- schema.scope.keys(int, listConf.name)
//          scalar    <- itemValue.cast[ScalarValue].toTraversable
//          string <- scalar.value match {
//            case SString(value)      => List(value)
//            case SStringList(values) => values
//            case _                   => Nil
//          }
//        } yield {
//          Write.Append(key, SString(string), int.timestamp)
//        }
//      case _ => Traversable.empty
//    }
//  }

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      visitorKey      <- schema.scope.keys(request, listConf.name).headOption
      interactedValue <- features.get(visitorKey)
      interactedList  <- interactedValue.cast[BoundedListValue]
      itemKey = Key(Tag(ItemScope.scope, id.id.value), fieldConf.name, Tenant(request.tenant))
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

  implicit val interWithDecoder: Decoder[InteractedWithSchema] = deriveDecoder
}
