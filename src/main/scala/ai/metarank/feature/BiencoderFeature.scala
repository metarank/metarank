package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.BiencoderFeature.BiencoderSchema
import ai.metarank.fstore.Persistence
import ai.metarank.ml.onnx.distance.DistanceFunction
import ai.metarank.ml.onnx.distance.DistanceFunction.CosineDistance
import ai.metarank.ml.onnx.encoder.{Encoder, EncoderType}
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{StringField, StringListField}
import ai.metarank.model.{Event, Feature, FeatureSchema, FeatureValue, Field, Key, MValue, ScopeType, Write}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scalar.SDoubleList
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}
import io.circe.Decoder

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class BiencoderFeature(schema: BiencoderSchema, encoder: Encoder) extends ItemFeature with Logging {
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
          field <- e.fieldsMap.get(schema.itemField)
          string <- field match {
            case Field.StringField(_, value)     => Some(value)
            case Field.StringListField(_, value) => Some(value.mkString(" "))
            case _                               => None
          }
        } yield {
          val encoded = encoder.encode(e.item, string)
          Put(Key(ItemScope(e.item), conf.name), e.timestamp, SDoubleList(encoded))
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
    val queryOption = request.fieldsMap.get(schema.rankingField).collect {
      case StringField(_, value)     => value
      case StringListField(_, value) => value.mkString(" ")
    }
    queryOption match {
      case Some(queryString) =>
        val queryEmbedding = encoder.encode(queryString)
        request.items.toList.map(item => {
          features.get(Key(ItemScope(item.id), conf.name)) match {
            case Some(ScalarValue(_, ts, SDoubleList(emb))) =>
              MValue(schema.name.value, schema.distance.dist(queryEmbedding, emb))
            case _ => SingleValue.missing(schema.name)
          }
        })
      case None => request.items.toList.map(_ => SingleValue.missing(schema.name))
    }
  }
}

object BiencoderFeature {
  import ai.metarank.util.DurationJson._
  case class BiencoderSchema(
      name: FeatureName,
      rankingField: String,
      itemField: String,
      encoder: EncoderType,
      distance: DistanceFunction,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    lazy val scope: ScopeType = ItemScopeType

    override def create(): IO[BaseFeature] = Encoder.create(encoder).map(enc => BiencoderFeature(this, enc))
  }

  object BiencoderSchema {
    implicit val biencSchemaDecoder: Decoder[BiencoderSchema] = Decoder.instance(c =>
      for {
        name         <- c.downField("name").as[FeatureName]
        rankingField <- c.downField("rankingField").as[String]
        itemField    <- c.downField("itemField").as[String]
        encoder      <- c.downField("encoder").as[EncoderType]
        distance     <- c.downField("distance").as[Option[DistanceFunction]]
        refresh      <- c.downField("refresh").as[Option[FiniteDuration]]
        ttl          <- c.downField("rrl").as[Option[FiniteDuration]]
      } yield {
        BiencoderSchema(
          name = name,
          rankingField = rankingField,
          itemField = itemField,
          encoder = encoder,
          distance = distance.getOrElse(CosineDistance),
          refresh = refresh,
          ttl = ttl
        )
      }
    )
  }

}
