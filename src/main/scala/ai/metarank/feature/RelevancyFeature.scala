package ai.metarank.feature

import ai.metarank.feature.MetaFeature.StatelessFeature
import ai.metarank.feature.RelevancyFeature.RelevancySchema
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.{Event, FeatureSchema, FeatureScope, FieldName, FieldSchema, ItemId, MValue}
import ai.metarank.model.MValue.SingleValue
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.findify.featury.model.Write.Put
import io.findify.featury.model.{FeatureConfig, FeatureValue, Key, SDouble, ScalarValue}

import scala.concurrent.duration.FiniteDuration

case class RelevancyFeature(schema: RelevancySchema) extends StatelessFeature {
  override def dim: Int = 1

  override def fields: List[FieldSchema] = Nil

  override def states: List[FeatureConfig] = Nil

  override def writes(event: Event): Iterable[Put] = Nil

  override def value(
      request: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = SingleValue(schema.name, id.relevancy.getOrElse(0.0))

}

object RelevancyFeature {
  case class RelevancySchema(name: String) extends FeatureSchema {
    lazy val refresh: Option[FiniteDuration] = None
    lazy val ttl: Option[FiniteDuration]     = None
    lazy val scope: FeatureScope             = ItemScope
  }

  implicit val relDecoder: Decoder[RelevancySchema] = deriveDecoder
}
