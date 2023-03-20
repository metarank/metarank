package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingFeature
import ai.metarank.feature.RefererFeature.RefererSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.{FeedbackEvent, InteractionEvent, RankingEvent, UserEvent}
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.{MapValue, ScalarValue}
import ai.metarank.model.Field.StringField
import ai.metarank.model.FieldName.EventType
import ai.metarank.model.FieldName.EventType.{AnyEvent, Interaction, Ranking, User}
import ai.metarank.model.Identifier.{SessionId, UserId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, VectorValue}
import ai.metarank.model.Scalar.{SBoolean, SString}
import ai.metarank.model.Scope.{SessionScope, UserScope}
import ai.metarank.model.Write.{Put, PutTuple}
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, Field, FieldName, Key, MValue, ScopeType, Write}
import ai.metarank.util.Logging
import better.files.{File, Resource}
import cats.Id
import cats.effect.IO
import com.snowplowanalytics.refererparser.{
  CreateParser,
  EmailMedium,
  InternalMedium,
  PaidMedium,
  Parser,
  SearchMedium,
  SocialMedium,
  UnknownMedium
}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class RefererFeature(schema: RefererSchema, parser: Parser) extends RankingFeature with Logging {

  val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  val possibleValues = Map(
    UnknownMedium.value  -> 0,
    SearchMedium.value   -> 1,
    InternalMedium.value -> 2,
    SocialMedium.value   -> 3,
    EmailMedium.value    -> 4,
    PaidMedium.value     -> 5
  )

  override val dim = SingleDim

  override val states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] = IO {
    event match {
      case event: RankingEvent if schema.source.event == Ranking => writeField(event, event.user, event.session)
      case event: InteractionEvent if schema.source.event == Interaction(event.`type`) =>
        writeField(event, event.user, event.session)
      case event: FeedbackEvent if schema.source.event == AnyEvent =>
        writeField(event, event.user, event.session)
      case _ => Iterable.empty
    }
  }

  def writeField(event: Event, user: Option[UserId], session: Option[SessionId]): Iterable[Put] = {
    for {
      key <- schema.scope match {
        case ScopeType.UserScopeType    => user.map(u => Key(UserScope(u), conf.name))
        case ScopeType.SessionScopeType => session.map(s => Key(SessionScope(s), conf.name))
        case _                          => None
      }
      field <- event.fieldsMap.get(schema.source.field)
      ref <- field match {
        case StringField(_, value) => Some(value)
        case _ =>
          logger.warn(s"expected string field type, but got $field")
          None
      }
      parsed <- parser.parse(ref)
    } yield {
      Put(key, event.timestamp, SString(parsed.medium.value))
    }
  }

  override def valueKeys(event: RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue]): MValue = {
    val result = for {
      key <- schema.scope match {
        case ScopeType.UserScopeType    => request.user.map(u => Key(UserScope(u), conf.name))
        case ScopeType.SessionScopeType => request.session.map(s => Key(SessionScope(s), conf.name))
        case _                          => None
      }
      mediumString <- features.get(key).flatMap {
        case ScalarValue(_, _, SString(medium)) => Some(medium)
        case _                                  => None
      }
      index <- possibleValues.get(mediumString)
    } yield {
      CategoryValue(schema.name, mediumString, index)
    }
    result.getOrElse(CategoryValue(schema.name, "unknown", 0))
  }

}

object RefererFeature {
  import ai.metarank.util.DurationJson._

  case class RefererSchema(
      name: FeatureName,
      source: FieldName,
      scope: ScopeType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema
      with Logging {
    override def create(): IO[BaseFeature] = createParser().map(p => RefererFeature(this, p))

    def createParser() = for {
      json         <- IO(Resource.my.getAsString("/referers.json"))
      file         <- IO(File.newTemporaryFile("referers", ".json").deleteOnExit())
      _            <- IO(file.write(json))
      _            <- info("loaded referers.json from resources")
      parserEither <- CreateParser[IO].create(file.toString())
      parser       <- IO.fromEither(parserEither)
    } yield {
      parser
    }

  }

  implicit val refererDecoder: Decoder[RefererSchema] = deriveDecoder[RefererSchema]
    .ensure(validType, "source type can be only interaction or ranking")
    .ensure(validScope, "scope can be only user or session")
    .withErrorMessage("cannot parse a feature definition of type 'referer'")

  implicit val refererEncoder: Encoder[RefererSchema] = deriveEncoder

  private def validType(schema: RefererSchema) = schema.source.event match {
    case EventType.Interaction(_) => true
    case EventType.Ranking        => true
    case _                        => false
  }

  private def validScope(schema: RefererSchema) = schema.scope match {
    case ScopeType.UserScopeType    => true
    case ScopeType.SessionScopeType => true
    case _                          => false
  }

}
