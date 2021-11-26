package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, MetadataEvent}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, UserScope}
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.{
  Event,
  FeatureSchema,
  FeatureScope,
  Field,
  FieldName,
  FieldSchema,
  ItemId,
  MValue,
  SessionId,
  UserId
}
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

case class InteractedWithFeature(schema: InteractedWithSchema) extends MFeature with Logging {
  override def dim: Int = 1

  // stores last interactions of customer
  val listConf = BoundedListConfig(
    scope = Scope(schema.scope.value),
    name = FeatureName(schema.name + s"_last_${schema.interaction}"),
    count = schema.count.getOrElse(10),
    duration = schema.duration.getOrElse(24.hours),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  val fieldConf = ScalarConfig(
    scope = Scope(ItemScope.value),
    name = FeatureName(s"${schema.name}_field_${schema.field.field}"),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(listConf, fieldConf)

  // load what was interacted with
  override def prekeys(request: Event.RankingEvent): Traversable[Key] =
    keyOf(request.user, request.session, listConf.name, request.tenant)

  // then load all items with defined field
  override def keys(request: Event.RankingEvent, prestate: Map[Key, FeatureValue]): Traversable[Key] = {
    val interacted = for {
      key        <- keyOf(request.user, request.session, listConf.name, request.tenant).toTraversable
      interacted <- prestate.get(key).toTraversable
      list       <- interacted.cast[BoundedListValue].toTraversable
      item       <- list.values.map(_.value).collect { case SString(value) => ItemId(value) }
    } yield {
      keyOf(ItemScope, item, fieldConf.name, request.tenant)
    }
    val requested = for {
      item <- request.items
    } yield {
      keyOf(ItemScope, item.id, fieldConf.name, request.tenant)
    }
    val visitorKey = keyOf(request.user, request.session, listConf.name, request.tenant)
    interacted ++ requested ++ visitorKey.toList
  }

  override def fields: List[FieldSchema] = List(StringFieldSchema(schema.field))

  override def writes(event: Event): Traversable[Write] = {
    event match {
      case int: InteractionEvent =>
        for {
          key <- keyOf(int.user, int.session, listConf.name, int.tenant).toTraversable
          if int.`type` == schema.interaction
        } yield {
          Write.Append(key, SString(int.item.value), int.timestamp)
        }
      case meta: MetadataEvent =>
        for {
          field <- meta.fields.find(_.name == schema.field.field)
          key = keyOf(ItemScope, meta.item, fieldConf.name, meta.tenant)
          value <- field match {
            case Field.StringField(_, value)      => Some(SString(value))
            case Field.StringListField(_, values) => Some(SStringList(values))
            case _                                => None
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
  }

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemId
  ): MValue = {
    val result = for {
      visitorKey      <- keyOf(request.user, request.session, listConf.name, request.tenant)
      interactedValue <- state.get(visitorKey)
      interactedList  <- interactedValue.cast[BoundedListValue]
      itemFieldValue  <- state.get(keyOf(ItemScope, id, fieldConf.name, request.tenant)).flatMap(_.cast[ScalarValue])
    } yield {
      val items    = interactedList.values.map(_.value).collect { case SString(value) => ItemId(value) }
      val itemKeys = items.map(item => keyOf(ItemScope, item, fieldConf.name, request.tenant))
      val interactedFields = itemKeys
        .flatMap(state.get)
        .collect {
          case ScalarValue(_, _, SString(value))      => List(value)
          case ScalarValue(_, _, SStringList(values)) => values
        }
        .flatten
      val itemFields = itemFieldValue.value match {
        case SString(value)      => List(value)
        case SStringList(values) => values
        case _                   => Nil
      }
      val counts = interactedFields.groupBy(identity).map { case (k, v) => k -> v.size }
      val value  = itemFields.foldLeft(0)((acc, next) => acc + counts.getOrElse(next, 0))
      SingleValue(schema.name, value)
    }
    result.getOrElse(SingleValue(schema.name, 0))
  }

  def keyOf(user: UserId, session: SessionId, feature: FeatureName, tenant: String): Option[Key] = schema.scope match {
    case UserScope    => Some(keyOf(UserScope.value, user.value, feature.value, tenant))
    case SessionScope => Some(keyOf(SessionScope.value, session.value, feature.value, tenant))
    case _            => None
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
