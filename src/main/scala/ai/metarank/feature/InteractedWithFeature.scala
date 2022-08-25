package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, ItemEvent, ItemRelevancy}
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.{BoundedListValue, ScalarValue}
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{
  Event,
  FeatureKey,
  FeatureSchema,
  FeatureValue,
  Field,
  FieldName,
  Key,
  MValue,
  ScopeType,
  Write
}
import ai.metarank.model.Identifier._
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scalar.{SString, SStringList}
import ai.metarank.model.Scope.{ItemScope, SessionScope, UserScope}
import ai.metarank.model.ScopeType.{ItemScopeType, SessionScopeType, UserScopeType}
import ai.metarank.model.Write.{Append, Put}
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

case class InteractedWithFeature(schema: InteractedWithSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  // stores last interactions of customer
  val lastValues = BoundedListConfig(
    scope = schema.scope,
    name = FeatureName(schema.name.value + s"_last"),
    count = schema.count.getOrElse(100),
    duration = schema.duration.getOrElse(24.hours),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val itemValues = ScalarConfig(
    scope = ItemScopeType,
    name = FeatureName(schema.name.value + s"_field"),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(lastValues, itemValues)

  override def writes(event: Event, features: Persistence): IO[Iterable[Write]] =
    event match {
      case item: ItemEvent =>
        IO {
          for {
            field <- item.fieldsMap.get(schema.field.field).toSeq
            string = field match {
              case Field.StringField(_, value)     => List(value)
              case Field.StringListField(_, value) => value
              case _                               => Nil
            }
          } yield {
            Put(
              key = Key(ItemScope(item.item), itemValues.name),
              ts = event.timestamp,
              value = SStringList(string)
            )
          }
        }
      case int: InteractionEvent if int.`type` == schema.interaction =>
        for {
          feature <- IO.fromOption(features.scalars.get(FeatureKey(itemValues.scope, itemValues.name)))(
            new Exception(s"feature not mapped")
          )
          scalar <- feature.computeValue(Key(ItemScope(int.item), itemValues.name), int.timestamp)
        } yield {
          for {
            key <- writeKey(int, lastValues)
          } yield {
            val strings = scalar match {
              case Some(ScalarValue(_, _, SString(value)))      => List(value)
              case Some(ScalarValue(_, _, SStringList(values))) => values
              case _                                            => Nil
            }
            Append(key, SStringList(strings), int.timestamp)
          }
        }
      case _ => IO.pure(Nil)
    }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] =
    makeVisitorKey(event).toList ++ event.items.map(ir => makeItemKey(event, ir.id)).toList

  private def makeVisitorKey(request: Event.RankingEvent) = schema.scope match {
    case SessionScopeType => request.session.map(s => Key(SessionScope(s), lastValues.name))
    case UserScopeType    => Some(Key(UserScope(request.user), lastValues.name))
    case _                => None
  }

  private def makeItemKey(request: Event.RankingEvent, id: ItemId) = Key(ItemScope(id), itemValues.name)

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: ItemRelevancy): MValue = ???

  override def values(request: Event.RankingEvent, features: Map[Key, FeatureValue]): List[MValue] = {
    val visitorMap = (for {
      visitorKey      <- makeVisitorKey(request)
      interactedValue <- features.get(visitorKey)
      interactedList  <- interactedValue.cast[BoundedListValue]
    } yield {
      val interactedValues = interactedList.values.map(_.value).collect { case SString(value) => value }
      val sum              = interactedValues.size.toDouble
      interactedValues.groupBy(identity).map { case (k, v) => k -> v.size / sum }
    }).getOrElse(Map.empty[String, Double])
    for {
      item <- request.items.toList
    } yield {
      val itemKey = makeItemKey(request, item.id)
      val result = for {
        itemFieldValue <- features.get(itemKey).flatMap(_.cast[ScalarValue])
      } yield {
        val itemValues = itemFieldValue.value match {
          case SString(value)      => List(value)
          case SStringList(values) => values
          case _                   => Nil
        }
        val value = itemValues.foldLeft(0.0)((acc, next) => acc + visitorMap.getOrElse(next, 0.0))
        SingleValue(schema.name, value)
      }
      result.getOrElse(SingleValue(schema.name, 0))
    }

  }

}

object InteractedWithFeature {
  import ai.metarank.util.DurationJson._
  case class InteractedWithSchema(
      name: FeatureName,
      interaction: String,
      field: FieldName,
      scope: ScopeType,
      count: Option[Int],
      duration: Option[FiniteDuration],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val interWithDecoder: Decoder[InteractedWithSchema] =
    deriveDecoder[InteractedWithSchema]
      .ensure(onlyItem, "can only be applied to item fields")
      .ensure(onlyUserSession, "can only be scoped to user/session")
      .withErrorMessage("cannot parse a feature definition of type 'interacted_with'")

  def onlyItem(schema: InteractedWithSchema) = schema.field.event match {
    case EventType.Item => true
    case _              => false
  }

  def onlyUserSession(schema: InteractedWithSchema) = schema.scope match {
    case ScopeType.GlobalScopeType  => false
    case ScopeType.ItemScopeType    => false
    case ScopeType.UserScopeType    => true
    case ScopeType.SessionScopeType => true
  }
}
