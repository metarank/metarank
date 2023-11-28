package ai.metarank.feature

import ai.metarank.feature.InteractedWithFeature.InteractedWithSchema
import ai.metarank.feature.BaseFeature.{ItemFeature, ValueMode}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.{SingleDim, VectorDim}
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, ItemEvent, RankItem}
import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.{BoundedListValue, ScalarValue}
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.MValue.{SingleValue, VectorValue}
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
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import shapeless.syntax.typeable._

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

case class InteractedWithFeature(schema: InteractedWithSchema) extends ItemFeature with Logging {
  override val dim = VectorDim(schema.field.size)

  // stores last interactions of customer
  val interactions = BoundedListConfig(
    scope = schema.scope,
    name = FeatureName(schema.name.value + s"_interactions"),
    count = schema.count.getOrElse(100),
    duration = schema.duration.getOrElse(24.hours),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val fields = schema.field
    .map(field =>
      field.field -> ScalarConfig(
        scope = ItemScopeType,
        name = FeatureName(schema.name.value + s"_${field.field}"),
        refresh = schema.refresh.getOrElse(0.seconds),
        ttl = schema.ttl.getOrElse(90.days)
      )
    )
    .toMap
  override val states: List[FeatureConfig] = List(interactions) ++ fields.values.toList

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] =
    event match {
      case item: ItemEvent =>
        IO {
          for {
            field         <- item.fields
            scalarFeature <- fields.get(field.name)
            stringValues = field match {
              case Field.StringField(_, value)     => List(value)
              case Field.StringListField(_, value) => value
              case _                               => Nil
            }
          } yield {
            Put(
              key = Key(ItemScope(item.item), scalarFeature.name),
              ts = event.timestamp,
              value = SStringList(stringValues)
            )
          }
        }
      case int: InteractionEvent if int.`type` == schema.interaction =>
        IO {
          for {
            key <- makeVisitorKey(int.user, int.session)
          } yield {
            Append(key, SString(int.item.value), int.timestamp)
          }
        }
      case _ => IO.pure(Nil)
    }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = {
    // list of items user interacted with
    val visitorItems = makeVisitorKey(event.user, event.session).toList

    // fields of items in the ranking event
    val itemFields = for {
      (_, fieldFeature) <- fields
      item              <- event.items.toList
    } yield {
      Key(ItemScope(item.id), fieldFeature.name)
    }
    visitorItems ++ itemFields.toList
  }

  override def valueKeys2(event: Event.RankingEvent, features: Map[Key, FeatureValue]): Iterable[Key] = for {
    visitorKey      <- makeVisitorKey(event.user, event.session).toSeq
    interactedItems <- features.get(visitorKey).toSeq
    item <- interactedItems match {
      case BoundedListValue(_, _, values, _) => values.map(_.value).collect { case SString(id) => id }
      case _                                 => Nil
    }
    (_, itemFieldFeature) <- fields
  } yield {
    Key(ItemScope(ItemId(item)), itemFieldFeature.name)
  }

  private def makeVisitorKey(user: Option[UserId], session: Option[SessionId]) = schema.scope match {
    case SessionScopeType => session.map(s => Key(SessionScope(s), interactions.name))
    case UserScopeType    => user.map(u => Key(UserScope(u), interactions.name))
    case _                => None
  }

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: RankItem): MValue = ???

  override def values(request: Event.RankingEvent, features: Map[Key, FeatureValue], mode: ValueMode): List[MValue] = {
    val visitorFields = (for {
      visitorProfileKey         <- makeVisitorKey(request.user, request.session).toList
      interactedItemsValue      <- features.get(visitorProfileKey).toList
      interactedList            <- interactedItemsValue.cast[BoundedListValue].toList
      (fieldName, fieldFeature) <- fields
    } yield {
      val visitorFieldInteractedValues = interactedList.values
        .map(_.value)
        .collect { case SString(value) => ItemId(value) }
        .flatMap(item => features.get(Key(ItemScope(item), fieldFeature.name)))
        .collect { case ScalarValue(_, _, SStringList(values), _) => values }
        .flatten
      fieldName -> visitorFieldInteractedValues.groupMapReduce(identity)(_ => 1)(_ + _)
    }).toMap

    for {
      item <- request.items.toList.map(_.id)
    } yield {
      val intersection = for {
        (fieldName, fieldScalarFeature) <- fields
      } yield {
        val visitorFieldValue = visitorFields.getOrElse(fieldName, Map.empty)
        val itemFeature = features
          .get(Key(ItemScope(item), fieldScalarFeature.name))
          .collect { case ScalarValue(_, _, SStringList(values), _) => values }
          .getOrElse(List.empty[String])
        itemFeature.foldLeft(0.0)((cnt, fieldValue) => cnt + visitorFieldValue.getOrElse(fieldValue, 0))
      }
      VectorValue(schema.name, intersection.toArray, dim)
    }
  }

}

object InteractedWithFeature {
  import ai.metarank.util.DurationJson._
  case class InteractedWithSchema(
      name: FeatureName,
      interaction: String,
      field: List[FieldName],
      scope: ScopeType,
      count: Option[Int],
      duration: Option[FiniteDuration],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override def create(): IO[BaseFeature] = IO.pure(InteractedWithFeature(this))
  }

  implicit val interWithDecoder: Decoder[InteractedWithSchema] = Decoder.instance(c =>
    for {
      name        <- c.downField("name").as[FeatureName]
      interaction <- c.downField("interaction").as[String]
      fields <- c.downField("field").as[FieldName] match {
        case Left(_)      => c.downField("field").as[List[FieldName]]
        case Right(value) => Right(List(value))
      }
      scope    <- c.downField("scope").as[ScopeType]
      count    <- c.downField("count").as[Option[Int]]
      duration <- c.downField("duration").as[Option[FiniteDuration]]
      refresh  <- c.downField("refresh").as[Option[FiniteDuration]]
      ttl      <- c.downField("ttl").as[Option[FiniteDuration]]
    } yield {
      InteractedWithSchema(name, interaction, fields, scope, count, duration, refresh, ttl)
    }
  )
  deriveDecoder[InteractedWithSchema]
    .ensure(onlyItem, "can only be applied to item fields")
    .ensure(onlyUserSession, "can only be scoped to user/session")
    .withErrorMessage("cannot parse a feature definition of type 'interacted_with'")

  implicit val interWithEncoder: Encoder[InteractedWithSchema] = deriveEncoder

  def onlyItem(schema: InteractedWithSchema) = schema.field.forall(f =>
    f.event match {
      case EventType.Item => true
      case _              => false
    }
  )

  def onlyUserSession(schema: InteractedWithSchema) = schema.scope match {
    case ScopeType.GlobalScopeType   => false
    case ScopeType.ItemScopeType     => false
    case ScopeType.UserScopeType     => true
    case ScopeType.SessionScopeType  => true
    case ScopeType.FieldScopeType(_) => false
  }
}
