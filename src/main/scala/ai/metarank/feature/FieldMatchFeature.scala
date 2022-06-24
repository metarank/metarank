package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.matcher.{FieldMatcher, NgramMatcher}
import ai.metarank.flow.FieldStore
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldName.EventType._
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.{Event, FeatureSchema, FieldName, MValue}
import ai.metarank.util.{Logging, OneHotEncoder, TextAnalyzer}
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SString, SStringList, ScalarValue, Write}
import io.findify.featury.model.FeatureConfig.ScalarConfig
import io.findify.featury.model.Key.FeatureName
import io.findify.featury.model.Write.Put
import ai.metarank.model.Identifier._
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer

import java.util
import java.util.Comparator
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

case class FieldMatchFeature(schema: FieldMatchSchema) extends ItemFeature with Logging {
  override def dim: Int = 1

  private val conf = ScalarConfig(
    scope = schema.scope.scope,
    name = FeatureName(schema.name + "_" + schema.itemField.field),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def fields = List(schema.itemField, schema.rankingField)

  override def writes(event: Event, fields: FieldStore): Iterable[Write] = for {
    key   <- keyOf(event)
    field <- event.fields.find(_.name == schema.itemField.field)
    fieldValue <- field match {
      case StringField(_, value) => Some(SStringList(schema.method.tokenize(value).toList))
      case other =>
        logger.warn(s"field extractor ${schema.name} expects a string, but got $other in event $event")
        None
    }
  } yield {
    Put(key, event.timestamp, fieldValue)
  }

  // we have a batch method overridden, so ??? is deliberate
  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: Event.ItemRelevancy): MValue =
    ???

  override def values(request: Event.RankingEvent, features: Map[Key, FeatureValue]): List[MValue] = {
    val requestTokensOption = request.fieldsMap.get(schema.rankingField.field) match {
      case Some(StringField(_, value)) => Some(schema.method.tokenize(value))
      case None                        => None
      case other =>
        logger.warn(s"${schema.name}: expected string field type, but got $other")
        None
    }
    requestTokensOption match {
      case Some(requestTokens) =>
        for {
          item <- request.items.toList
        } yield {
          val result = for {
            key          <- keyOf(request, Some(item.id))
            featureValue <- features.get(key)
            itemStringTokens <- featureValue match {
              case ScalarValue(_, _, SStringList(value)) => Some(value.toArray)
              case other =>
                logger.warn(s"${schema.name}: expected string state, but got $other")
                None
            }
          } yield {
            itemStringTokens
          }
          result
            .map(itemTokens => SingleValue(schema.name, schema.method.score(requestTokens, itemTokens)))
            .getOrElse(SingleValue(schema.name, 0))
        }
      case None => List.fill(request.items.size)(SingleValue(schema.name, 0))
    }

  }
}

object FieldMatchFeature {
  import ai.metarank.util.DurationJson._
  case class FieldMatchSchema(
      name: String,
      rankingField: FieldName,
      itemField: FieldName,
      method: FieldMatcher,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = ItemScope
  }

  implicit val matchDecoder: Decoder[FieldMatcher] = Decoder.instance[FieldMatcher](c =>
    for {
      tpe <- c.downField("type").as[String]
      method <- tpe match {
        case "ngram" => NgramMatcher.ngramDecoder.apply(c)
        case "term"  => NgramMatcher.ngramDecoder.apply(c)
        case other   => Left(DecodingFailure(s"match method $other is not supported", c.history))
      }
    } yield {
      method
    }
  )

  implicit val fieldMatchDecoder: Decoder[FieldMatchSchema] = deriveDecoder[FieldMatchSchema]
    .ensure(
      pred = x => (x.rankingField.event == Ranking) && (x.itemField.event == Item),
      message = "ranking field can only be read from ranking event, and item field - only from metadata"
    )
    .withErrorMessage("cannot parse a feature definition of type 'field_match'")
}
