package ai.metarank.feature

import ai.metarank.feature.BaseFeature.{ItemFeature, ValueMode}
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.matcher.{FieldMatcher, NgramMatcher, TermMatcher}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldName.EventType._
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SStringList
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, Write}
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration._

case class FieldMatchFeature(schema: FieldMatchSchema) extends ItemFeature with Logging {
  override def dim = SingleDim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = FeatureName(schema.name.value + "_" + schema.itemField.field),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event): IO[Iterable[Write]] = IO {
    for {
      key   <- writeKey(event, conf)
      field <- event.fields.find(_.name == schema.itemField.field)
      fieldValue <- field match {
        case StringField(_, value)      => Some(SStringList(schema.method.tokenize(value).toList))
        case StringListField(_, values) => Some(SStringList(schema.method.tokenize(values.mkString(" ")).toList))
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a string, but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, fieldValue)
    }
  }
  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)
  // we have a batch method overridden, so ??? is deliberate
  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: Event.RankItem): MValue =
    ???

  override def values(request: Event.RankingEvent, features: Map[Key, FeatureValue], mode: ValueMode): List[MValue] = {
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
          val key = Key(ItemScope(item.id), conf.name)
          val result = for {
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
      name: FeatureName,
      rankingField: FieldName,
      itemField: FieldName,
      method: FieldMatcher,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = ItemScopeType
  }

  implicit val matchDecoder: Decoder[FieldMatcher] = Decoder.instance[FieldMatcher](c =>
    for {
      tpe <- c.downField("type").as[String]
      method <- tpe match {
        case "ngram" => NgramMatcher.ngramDecoder.apply(c)
        case "term"  => TermMatcher.termDecoder.apply(c)
        case other   => Left(DecodingFailure(s"match method $other is not supported", c.history))
      }
    } yield {
      method
    }
  )

  implicit val matchEncoder: Encoder[FieldMatcher] = Encoder.instance {
    case m: NgramMatcher =>
      NgramMatcher
        .ngramEncoder(m)
        .deepMerge(Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("term")))))
    case t: TermMatcher =>
      TermMatcher
        .termEncoder(t)
        .deepMerge(Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString("term")))))
    case _ => ???
  }

  implicit val fieldMatchDecoder: Decoder[FieldMatchSchema] = deriveDecoder[FieldMatchSchema]
    .ensure(
      pred = x => (x.rankingField.event == Ranking) && (x.itemField.event == Item),
      message = "ranking field can only be read from ranking event, and item field - only from metadata"
    )
  // .withErrorMessage("cannot parse a feature definition of type 'field_match'")

  implicit val fieldMatchEncoder: Encoder[FieldMatchSchema] = deriveEncoder[FieldMatchSchema]
}
