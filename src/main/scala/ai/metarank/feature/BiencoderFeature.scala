package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.BiencoderFeature.BiencoderSchema
import ai.metarank.fstore.Persistence
import ai.metarank.ml.onnx.encoder.{Encoder, EncoderType}
import ai.metarank.model.Dimension.SingleDim
import ai.metarank.model.Event.ItemEvent
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.{Event, Feature, FeatureSchema, FeatureValue, Field, Key, MValue, ScopeType, Write}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.util.Logging
import cats.effect.{IO, Ref}

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

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = ???

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
          val encoded = encoder
          ???
        }

      case _ => None
    }
  }

  override def value(request: Event.RankingEvent, features: Map[Key, FeatureValue], id: Event.RankItem): MValue = ???
}

object BiencoderFeature {
  case class BiencoderSchema(
      name: FeatureName,
      rankingField: String,
      itemField: String,
      encoder: EncoderType,
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema {
    lazy val scope: ScopeType = ItemScopeType

    override def create(): IO[BaseFeature] = for {
      encoder <- Encoder.create(encoder)
    } yield {
      BiencoderFeature(this, encoder)
    }
  }


}
