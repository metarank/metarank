package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.FieldMatchCrossEncoderFeature.FieldMatchCrossEncoderSchema
import ai.metarank.fstore.Persistence
import ai.metarank.ml.onnx.{EmbeddingCache, Normalize, ScoreCache}
import ai.metarank.ml.onnx.ModelHandle.{HuggingFaceHandle, LocalModelHandle}
import ai.metarank.ml.onnx.Normalize.NoopNormalize
import ai.metarank.ml.onnx.distance.DistanceFunction
import ai.metarank.ml.onnx.distance.DistanceFunction.CosineDistance
import ai.metarank.ml.onnx.encoder.EncoderConfig.CrossEncoderConfig
import ai.metarank.ml.onnx.sbert.{OnnxCrossEncoder, OnnxSession}
import ai.metarank.ml.onnx.sbert.OnnxCrossEncoder.SentencePair
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.FieldName.EventType.{Item, Ranking}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.model.{Event, Feature, FeatureSchema, FeatureValue, Field, FieldName, Key, MValue, ScopeType, Write}
import ai.metarank.util.Logging
import cats.effect.IO
import io.circe.{Decoder, DecodingFailure}

import java.io.File
import scala.concurrent.duration._

case class FieldMatchCrossEncoderFeature(
    schema: FieldMatchCrossEncoderSchema,
    encoder: Option[OnnxCrossEncoder],
    cache: ScoreCache
) extends ItemFeature
    with Logging {
  override def dim = SingleDim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )

  override def states: List[Feature.FeatureConfig] = List(conf)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] =
    event.items.toList.map(item => Key(ItemScope(item.id), conf.name))

  override def writes(event: Event, store: Persistence): IO[Iterable[Write]] = IO {
    event match {
      case e: ItemEvent =>
        for {
          field <- e.fieldsMap.get(schema.itemField.field)
          string <- field match {
            case Field.StringField(_, value)     => Some(value)
            case Field.StringListField(_, value) => Some(value.mkString(" "))
            case _                               => None
          }
        } yield {
          Put(Key(ItemScope(e.item), conf.name), e.timestamp, SString(string))
        }

      case _ => None
    }
  }

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: Event.RankItem): MValue = ???

  override def values(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      mode: BaseFeature.ValueMode
  ): List[MValue] = {
    val queryOption = request.fieldsMap.get(schema.rankingField.field).collect {
      case StringField(_, value)     => value
      case StringListField(_, value) => value.mkString(" ")
    }
    val result = for {
      queryString <- queryOption
    } yield {
      val fieldValues = request.items.toList
        .flatMap(item => {
          features.get(Key(ItemScope(item.id), conf.name)) match {
            case Some(ScalarValue(_, _, SString(value), _)) =>
              Some(item.id -> SentencePair(queryString, value))
            case _ =>
              None
          }
        })

      val fieldItems      = fieldValues.map(_._1)
      val cached          = fieldItems.flatMap(item => cache.get(item, queryString).map(score => item -> score)).toMap
      val nonCachedValues = fieldValues.filterNot(fv => cached.contains(fv._1))
      val nonCached = encoder match {
        case Some(enc) if nonCachedValues.nonEmpty =>
          nonCachedValues.map(_._1).zip(enc.encode(nonCachedValues.map(_._2).toArray)).toMap
        case _ => Map.empty
      }
      val encoded: Map[ItemId, Float] = cached ++ nonCached
      val raw = request.items.toList.map(item => {
        encoded.get(item.id) match {
          case Some(score) => SingleValue(schema.name, score)
          case None        => SingleValue.missing(schema.name)
        }

      })
      schema.norm.scale(raw)
    }
    result.getOrElse(request.items.toList.map(_ => SingleValue.missing(schema.name)))
  }
}

object FieldMatchCrossEncoderFeature extends Logging {
  import ai.metarank.util.DurationJson._

  case class FieldMatchCrossEncoderSchema(
      name: FeatureName,
      rankingField: FieldName,
      itemField: FieldName,
      method: CrossEncoderConfig,
      distance: DistanceFunction,
      norm: Normalize = NoopNormalize,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    lazy val scope: ScopeType = ItemScopeType

    override def create(): IO[BaseFeature] = for {
      session <- method.model match {
        case Some(handle) => OnnxSession.load(handle, 0, method.modelFile, method.tokenizerFile).map(Option.apply)
        case None         => IO.none
      }
      cache <- method.cache match {
        case Some(path) if !(new File(path).exists()) =>
          info(s"cache file $path missing, ignoring") *> IO.pure(ScoreCache.empty)

        case Some(path) => ScoreCache.fromCSV(path, ',', 0)
        case None       => IO.pure(ScoreCache.empty)
      }
    } yield {
      FieldMatchCrossEncoderFeature(this, session.map(OnnxCrossEncoder.apply), cache)
    }
  }

  implicit val crossSchemaDecoder: Decoder[FieldMatchCrossEncoderSchema] = Decoder.instance(c =>
    for {
      name <- c.downField("name").as[FeatureName]
      rankingField <- c.downField("rankingField").as[FieldName].flatMap {
        case ok @ FieldName(Ranking, _) => Right(ok)
        case other                      => Left(DecodingFailure(s"expected ranking field, but got $other", c.history))
      }
      itemField <- c.downField("itemField").as[FieldName].flatMap {
        case ok @ FieldName(Item, _) => Right(ok)
        case other                   => Left(DecodingFailure(s"expected item field, but got $other", c.history))
      }
      method   <- c.downField("method").as[CrossEncoderConfig]
      distance <- c.downField("distance").as[Option[DistanceFunction]]
      refresh  <- c.downField("refresh").as[Option[FiniteDuration]]
      ttl      <- c.downField("rrl").as[Option[FiniteDuration]]
      norm     <- c.downField("norm").as[Option[Normalize]]
    } yield {
      FieldMatchCrossEncoderSchema(
        name = name,
        rankingField = rankingField,
        itemField = itemField,
        method = method,
        distance = distance.getOrElse(CosineDistance),
        refresh = refresh,
        ttl = ttl,
        norm = norm.getOrElse(NoopNormalize)
      )
    }
  )
}
