package ai.metarank.feature

import ai.metarank.feature.BaseFeature.RankingFeature
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.ua.{BotField, BrowserField, OSField, PlatformField}
import ai.metarank.flow.FieldStore
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, ItemId, MValue, UserId}
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.OneHotEncoder
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.FeatureName
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SString, SStringList, ScalarValue}
import ua_parser.{Client, Parser}

import scala.concurrent.duration._

case class UserAgentFeature(schema: UserAgentSchema) extends RankingFeature {
  lazy val parser       = new Parser()
  val names             = schema.field.possibleValues.map(value => s"${schema.name}_$value")
  override def dim: Int = schema.field.dim

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(schema.source)

  override def writes(event: Event, user: FieldStore[UserId], item: FieldStore[ItemId]): Iterable[Put] = Nil

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue]
  ): MValue = {
    request.fieldsMap.get(schema.source.field) match {
      case Some(StringField(_, value)) =>
        val client = parser.parse(value)
        val field  = schema.field.value(client)
        VectorValue(names, OneHotEncoder.fromValues(field, schema.field.possibleValues, dim), dim)
      case _ =>
        VectorValue(names, OneHotEncoder.empty(dim), dim)

    }
  }
}

object UserAgentFeature {
  import ai.metarank.util.DurationJson._

  case class UserAgentSchema(
      name: String,
      source: FieldName,
      field: UAField,
      scope: FeatureScope,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

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

  implicit val uaDecoder: Decoder[UserAgentSchema] = deriveDecoder

}
