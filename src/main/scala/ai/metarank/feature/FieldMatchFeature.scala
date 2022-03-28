package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemStatelessFeature
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldName.{Metadata, Ranking}
import ai.metarank.model.FieldSchema.StringFieldSchema
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, MValue}
import ai.metarank.util.{Logging, OneHotEncoder}
import cats.data.NonEmptyList
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
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
  SStringList,
  ScalarValue,
  Write
}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.FeatureName
import io.findify.featury.model.Write.Put

import scala.concurrent.duration._

case class FieldMatchFeature(schema: FieldMatchSchema) extends ItemStatelessFeature with Logging {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name + "_" + schema.itemField.field),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(StringFieldSchema(schema.itemField), StringFieldSchema(schema.rankingField))

  override def writes(event: Event): Traversable[Write] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.itemField.field)
    fieldValue <- field match {
      case StringField(_, value) => Some(SString(value))
      case other =>
        logger.warn(s"field extractor ${schema.name} expects a string, but got $other in event $event")
        None
    }
  } yield {
    Put(key, event.timestamp, fieldValue)
  }

  override def value(request: Event.RankingEvent, state: Map[Key, FeatureValue], id: Event.ItemRelevancy): MValue = {
    val result = for {
      key          <- keyOf(request, Some(id.id))
      featureValue <- state.get(key)
      itemString <- featureValue match {
        case ScalarValue(_, _, SString(value)) => Some(value)
        case other =>
          logger.warn(s"${schema.name}: expected string state, but got $other")
          None
      }
      rankingString <- request.fieldsMap.get(schema.rankingField.field) match {
        case Some(StringField(_, value)) => Some(value)
        case None                        => None
        case other =>
          logger.warn(s"${schema.name}: expected string field type, but got $other")
          None
      }
    } yield {
      schema.method.matchScore(itemString, rankingString)
    }
    result match {
      case Some(score) => SingleValue(schema.name, score)
      case None        => SingleValue(schema.name, 0)
    }
  }
}

object FieldMatchFeature {
  import ai.metarank.util.DurationJson._
  case class FieldMatchSchema(
      name: String,
      rankingField: FieldName,
      itemField: FieldName,
      method: MatchMethod,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = ItemScope
  }

  sealed trait MatchMethod {
    def matchScore(a: String, b: String): Double
  }
  case class NgramMethod(n: Int) extends MatchMethod {
    val whitespace = "\\W".r
    def matchScore(a: String, b: String): Double = {
      val aNgrams      = ngram(a, n).toSet
      val bNgrams      = ngram(b, n).toSet
      val union        = aNgrams.union(bNgrams).size
      val intersection = aNgrams.intersect(bNgrams).size
      if (union > 0) intersection.toDouble / union.toDouble else 0.0
    }

    def ngram(input: String, n: Int): Array[String] = for {
      term      <- whitespace.split(input)
      substring <- term.sliding(n)
    } yield {
      substring
    }
  }

  implicit val ngramDecoder: Decoder[NgramMethod] = deriveDecoder[NgramMethod]

  implicit val matchDecoder: Decoder[MatchMethod] = Decoder.instance[MatchMethod](c =>
    for {
      tpe <- c.downField("type").as[String]
      method <- tpe match {
        case "ngram" => ngramDecoder.apply(c)
        case other   => Left(DecodingFailure(s"match method $other is not supported", c.history))
      }
    } yield {
      method
    }
  )

  implicit val fieldMatchDecoder: Decoder[FieldMatchSchema] = deriveDecoder[FieldMatchSchema].ensure(
    pred = x => (x.rankingField.event == Ranking) && (x.itemField.event == Metadata),
    message = "ranking field can only be read from ranking event, and item field - only from metadata"
  )
}
