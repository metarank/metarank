package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.Put
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import scala.concurrent.duration.FiniteDuration

case class RelevancyFeature(schema: RelevancySchema) extends ItemFeature {
  override def dim: Int = 1

  override def fields: List[FieldName] = Nil

  override def states: List[FeatureConfig] = Nil

  override def writes(event: Event, fields: Persistence): IO[Iterable[Put]] = IO.pure(Nil)

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = Nil

  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = SingleValue(schema.name, id.relevancy.getOrElse(0.0))

}

object RelevancyFeature {
  case class RelevancySchema(name: FeatureName) extends FeatureSchema {
    lazy val refresh: Option[FiniteDuration] = None
    lazy val ttl: Option[FiniteDuration]     = None
    lazy val scope: ScopeType                = ItemScopeType
  }

  implicit val relDecoder: Decoder[RelevancySchema] =
    deriveDecoder[RelevancySchema].withErrorMessage("cannot parse a feature definition of type 'relevancy'")
}
