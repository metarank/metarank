package ai.metarank.feature

import ai.metarank.feature.MetaFeature.StatelessFeature
import ai.metarank.feature.UserAgentFeature.UserAgentSchema
import ai.metarank.feature.ua.{OSField, PlatformField}
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, ItemId, MValue}
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.OneHotEncoder
import cats.data.NonEmptyList
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.parser._
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.FeatureName
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SString, SStringList, ScalarValue}
import ua_parser.{Client, Parser}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class UserAgentFeature(schema: UserAgentSchema) extends StatelessFeature {
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

  override def fields = List(StringFieldSchema(schema.source))

  override def writes(event: Event): Iterable[Put] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.source.field)
    fieldValue <- field match {
      case StringField(_, value) => Some(SString(value))
      case _                     => None
    }
    client = parser.parse(fieldValue.value)
    encodedValue <- schema.field.value(client)
  } yield {
    Put(key, event.timestamp, SString(encodedValue))
  }

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    val result = for {
      key   <- keyOf(request, Some(id.id))
      value <- state.get(key)
    } yield {
      value
    }
    result match {
      case Some(ScalarValue(_, _, SString(value))) =>
        // we cached the parsed UA in the past, no need to parse it
        VectorValue(names, OneHotEncoder.fromValue(value, schema.field.possibleValues, dim), dim)
      case None =>
        request.fieldsMap.get(schema.source.field) match {
          case Some(StringField(_, value)) =>
            val client = parser.parse(value)
            val field  = schema.field.value(client)
            VectorValue(names, OneHotEncoder.fromValues(field, schema.field.possibleValues, dim), dim)
          case _ =>
            VectorValue(names, OneHotEncoder.empty(dim), dim)

        }
      case _ => VectorValue(names, OneHotEncoder.empty(dim), dim)
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
      case Right(other)      => Left(DecodingFailure(s"UA field type $other is not yet supported", c.history))
    }
  )

  implicit val uaDecoder: Decoder[UserAgentSchema] = deriveDecoder

}
