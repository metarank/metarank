package ai.metarank.feature

import ai.metarank.feature.BaseFeature.{ItemFeature, ValueMode}
import ai.metarank.feature.FieldMatchFeature.FieldMatchSchema
import ai.metarank.feature.FieldMatchFeature.FieldMatcherType.{BM25MatcherType, NgramMatcherType, TermMatcherType}
import ai.metarank.feature.matcher.{BM25Matcher, FieldMatcher, NgramMatcher, TermMatcher}
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
import ai.metarank.util.{Logging, TextAnalyzer}
import cats.effect.IO
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder, deriveEncoder}

import scala.concurrent.duration._

case class FieldMatchFeature(schema: FieldMatchSchema, matcher: FieldMatcher) extends ItemFeature with Logging {
  override def dim = SingleDim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = FeatureName(schema.name.value + "_" + schema.itemField.field),
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] = IO {
    for {
      key   <- writeKey(event, conf)
      field <- event.fields.find(_.name == schema.itemField.field)
      fieldValue <- field match {
        case StringField(_, value)      => Some(SStringList(matcher.tokenize(value).toList))
        case StringListField(_, values) => Some(SStringList(matcher.tokenize(values.mkString(" ")).toList))
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
      case Some(StringField(_, value)) => Some(matcher.tokenize(value))
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
            .map(itemTokens => SingleValue(schema.name, matcher.score(requestTokens, itemTokens)))
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
      method: FieldMatcherType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    override val scope = ItemScopeType

    override def create(): IO[BaseFeature] = for {
      fm <- method.create()
    } yield {
      FieldMatchFeature(this, fm)
    }
  }

  sealed trait FieldMatcherType {
    def create(): IO[FieldMatcher]
  }
  object FieldMatcherType {
    case class NgramMatcherType(n: Int, language: String) extends FieldMatcherType {
      override def create(): IO[FieldMatcher] = for {
        analyzerOption <- IO.blocking(TextAnalyzer.analyzers.find(_.names.toList.contains(language)))
        analyzer       <- IO.fromOption(analyzerOption)(new Exception(s"language $language is not supported"))
      } yield {
        NgramMatcher(n, analyzer)
      }
    }
    case class TermMatcherType(language: String) extends FieldMatcherType {
      override def create(): IO[FieldMatcher] = for {
        analyzerOption <- IO.blocking(TextAnalyzer.analyzers.find(_.names.toList.contains(language)))
        analyzer       <- IO.fromOption(analyzerOption)(new Exception(s"language $language is not supported"))
      } yield {
        TermMatcher(analyzer)
      }
    }
    case class BM25MatcherType(language: String, termFreq: String) extends FieldMatcherType {
      override def create(): IO[FieldMatcher] = for {
        analyzerOption <- IO.blocking(TextAnalyzer.analyzers.find(_.names.toList.contains(language)))
        analyzer       <- IO.fromOption(analyzerOption)(new Exception(s"language $language is not supported"))
        tf             <- BM25Matcher.TermFreqDic.fromFile(termFreq)
      } yield {
        BM25Matcher(analyzer, tf)
      }
    }

  }

  implicit val ngramCodec: Codec[NgramMatcherType] = deriveCodec[NgramMatcherType]
  implicit val termCodec: Codec[TermMatcherType]   = deriveCodec[TermMatcherType]
  implicit val bm25Codec: Codec[BM25MatcherType]   = deriveCodec[BM25MatcherType]

  implicit val fieldMatcherTypeDecoder: Decoder[FieldMatcherType] = Decoder.instance(c =>
    c.downField("type").as[String] match {
      case Left(err)      => Left(err)
      case Right("ngram") => ngramCodec.apply(c)
      case Right("term")  => termCodec.apply(c)
      case Right("bm25")  => bm25Codec.apply(c)
      case Right(other)   => Left(DecodingFailure(s"field matching method '$other' is not supported", c.history))
    }
  )

  implicit val fieldMatcherTypeEncoder: Encoder[FieldMatcherType] = Encoder.instance {
    case x: NgramMatcherType => ngramCodec(x).deepMerge(withType("ngram"))
    case x: TermMatcherType  => termCodec(x).deepMerge(withType("term"))
    case x: BM25MatcherType  => bm25Codec(x).deepMerge(withType("bm25"))
  }

  implicit val fieldMatchDecoder: Decoder[FieldMatchSchema] = deriveDecoder[FieldMatchSchema]
    .ensure(
      pred = x => (x.rankingField.event == Ranking) && (x.itemField.event == Item),
      message = "ranking field can only be read from ranking event, and item field - only from metadata"
    )
    .withErrorMessage("cannot parse a feature definition of type 'field_match'")

  implicit val fieldMatchEncoder: Encoder[FieldMatchSchema] = deriveEncoder[FieldMatchSchema]

  def withType(t: String) = Json.fromJsonObject(JsonObject.fromMap(Map("type" -> Json.fromString(t))))
}
