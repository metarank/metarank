package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingFeature
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.feature.ua.{BotField, BrowserField, OSField, PlatformField}
import ai.metarank.flow.FieldStore
import ai.metarank.model.Event.{InteractionEvent, RankingEvent, UserEvent}
import ai.metarank.model.FeatureScope.SessionScope
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.FieldName.EventType.{Interaction, Ranking, User}
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, Field, FieldName, MValue}
import ai.metarank.util.Logging
import better.files.{File, Resource}
import cats.Id
import com.snowplowanalytics.refererparser.{
  CreateParser,
  EmailMedium,
  EmailReferer,
  InternalMedium,
  InternalReferer,
  PaidMedium,
  PaidReferer,
  SearchMedium,
  SearchReferer,
  SocialMedium,
  SocialReferer,
  UnknownMedium,
  UnknownReferer
}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.{
  BoundedListValue,
  CounterValue,
  FeatureConfig,
  FeatureValue,
  FrequencyValue,
  Key,
  MapValue,
  NumStatsValue,
  PeriodicCounterValue,
  SString,
  ScalarValue,
  Write
}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.Write.Put

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class RefererFeature(schema: RefererSchema) extends RankingFeature with Logging {
  lazy val parser = {
    val json = Resource.my.getAsString("/referers.json")
    val file = File.newTemporaryFile("referers", ".json").deleteOnExit()
    file.write(json)
    logger.info("loaded referers.json from resources")
    CreateParser[Id].create(file.toString()).right.get // YOLO
  }

  val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val names = List(
    UnknownMedium.value,
    SearchMedium.value,
    InternalMedium.value,
    SocialMedium.value,
    EmailMedium.value,
    PaidMedium.value
  )

  override def dim: Int = 6

  override def fields: List[FieldName] = List(schema.source)

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, fields: FieldStore): Traversable[Write] = event match {
    case event: UserEvent if schema.source.event == User                             => writeField(event)
    case event: RankingEvent if schema.source.event == Ranking                       => writeField(event)
    case event: InteractionEvent if schema.source.event == Interaction(event.`type`) => writeField(event)
    case _                                                                           => Traversable.empty
  }

  def writeField(event: Event): Traversable[Write] = {
    for {
      key   <- schema.scope.keys(event, conf.name)
      field <- event.fieldsMap.get(schema.source.field)
      ref <- field match {
        case StringField(_, value) => Some(value)
        case _ =>
          logger.warn(s"expected string field type, but got $field")
          None
      }
    } yield {
      Put(key, event.timestamp, SString(ref))
    }
  }

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue]): MValue = {
    val result = for {
      ref    <- fromState(request, features)
      parsed <- parser.parse(ref)
    } yield {
      val index = parsed.medium match {
        case UnknownMedium  => 0
        case SearchMedium   => 1
        case InternalMedium => 2
        case SocialMedium   => 3
        case EmailMedium    => 4
        case PaidMedium     => 5
      }
      val buffer = new Array[Double](6)
      buffer(index) = 1.0
      VectorValue(names, buffer, dim)
    }
    result.getOrElse(VectorValue.empty(names, dim))
  }

  def fromState(request: Event.RankingEvent, features: Map[Key, FeatureValue]): Option[String] = for {
    key      <- schema.scope.keys(request, conf.name).headOption
    refField <- features.get(key)
    ref <- refField match {
      case ScalarValue(_, _, SString(value)) => Some(value)
      case _                                 => None
    }
  } yield {
    ref
  }

}

object RefererFeature {
  import ai.metarank.util.DurationJson._

  case class RefererSchema(
      name: String,
      source: FieldName,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  implicit val refererDecoder: Decoder[RefererSchema] = deriveDecoder[RefererSchema]
    .ensure(validType, "source type can be only user, interaction or ranking")
    .ensure(validScope, "scope can be only user or session")

  private def validType(schema: RefererSchema) = schema.source.event match {
    case EventType.Item           => false
    case EventType.User           => true
    case EventType.Interaction(_) => true
    case EventType.Ranking        => true
  }

  private def validScope(schema: RefererSchema) = schema.scope match {
    case FeatureScope.UserScope    => true
    case FeatureScope.SessionScope => true
    case _                         => false
  }

}
