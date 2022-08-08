package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingFeature
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.ua.{BotField, BrowserField, OSField, PlatformField}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.Identifier.SessionId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.SessionScope
import ai.metarank.model.ScopeType.{SessionScopeType, UserScopeType}
import ai.metarank.model.Write.Put
import ai.metarank.util.OneHotEncoder
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder
import ua_parser.{Client, Parser}

import scala.concurrent.duration._

case class UserAgentFeature(schema: UserAgentSchema) extends RankingFeature {
  lazy val parser       = new Parser()
  val names             = schema.field.possibleValues.map(value => s"${schema.name.value}_$value")
  override def dim: Int = schema.field.dim

  val conf = ScalarConfig(
    scope = SessionScopeType,
    name = schema.name,
    refresh = 0.seconds
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(schema.source)

  override def writes(event: Event, fields: Persistence): IO[Iterable[Put]] = IO {
    event match {
      case feedback: Event.FeedbackEvent =>
        for {
          value   <- parse(feedback)
          session <- feedback.session
        } yield {
          Put(Key(SessionScope(event.env, session), conf.name), event.timestamp, SString(value))
        }
      case _ => None
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue]
  ): MValue = {
    request.session.flatMap(session => features.get(Key(SessionScope(request.env, session), conf.name))) match {
      case Some(ScalarValue(_, _, SString(stored))) =>
        VectorValue(names, OneHotEncoder.fromValues(List(stored), schema.field.possibleValues, dim), dim)
      case _ =>
        VectorValue(names, OneHotEncoder.fromValues(parse(request), schema.field.possibleValues, dim), dim)
    }
  }

  private def parse(event: Event): Option[String] = event.fieldsMap.get(schema.source.field) match {
    case Some(StringField(_, value)) => schema.field.value(parser.parse(value))
    case _                           => None
  }
}

object UserAgentFeature {
  import ai.metarank.util.DurationJson._

  case class UserAgentSchema(
      name: FeatureName,
      source: FieldName,
      field: UAField,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = ScopeType.SessionScopeType
  }

  trait UAField {
    lazy val dim: Int = possibleValues.size
    def possibleValues: List[String]
    def value(client: Client): Option[String]
  }
  implicit val uafieldDecoder: Decoder[UAField] = Decoder.instance(c =>
    c.as[String] match {
      case Left(value)       => Left(value)
      case Right("platform") => Right(PlatformField)
      case Right("os")       => Right(OSField)
      case Right("browser")  => Right(BrowserField)
      case Right("bot")      => Right(BotField)
      case Right(other)      => Left(DecodingFailure(s"UA field type $other is not yet supported", c.history))
    }
  )

  implicit val uaDecoder: Decoder[UserAgentSchema] =
    deriveDecoder[UserAgentSchema].withErrorMessage("cannot parse a feature definition of type 'ua'")

}
