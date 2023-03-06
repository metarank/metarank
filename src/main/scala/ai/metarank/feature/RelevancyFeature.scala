package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Dimension.{SingleDim, VectorDim}
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Field.NumberField
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, Field, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.MValue.{SingleValue, VectorValue}
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import scala.concurrent.duration.FiniteDuration

case class RelevancyFeature(schema: RelevancySchema) extends ItemFeature {
  override lazy val dim = SingleDim

  override def states: List[FeatureConfig] = Nil

  override def writes(event: Event, store: Persistence): IO[Iterable[Put]] = IO.pure(Nil)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = Nil

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: RankItem
  ): MValue = ???

  override def values(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      mode: BaseFeature.ValueMode
  ): List[MValue] = {
    request.items.toList.map(item => {
      item.fields
        .find(_.name == "relevancy")
        .collectFirst { case NumberField(_, value) =>
          value
        }
        .map(SingleValue(schema.name, _))
        .getOrElse(SingleValue.missing(schema.name))

    })
  }
}

object RelevancyFeature {
  case class RelevancySchema(name: FeatureName) extends FeatureSchema {
    lazy val refresh: Option[FiniteDuration] = None
    lazy val ttl: Option[FiniteDuration]     = None
    lazy val scope: ScopeType                = ItemScopeType
  }

  implicit val relDecoder: Decoder[RelevancySchema] =
    deriveDecoder[RelevancySchema].withErrorMessage("cannot parse a feature definition of type 'relevancy'")

  implicit val relEncoder: Encoder[RelevancySchema] = deriveEncoder
}
