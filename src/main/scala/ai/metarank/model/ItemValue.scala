package ai.metarank.model

import ai.metarank.FeatureMapping
import ai.metarank.feature.BaseFeature.{ItemFeature, RankingFeature}
import ai.metarank.model.Identifier.ItemId
import io.circe.Codec
import io.circe.generic.semiauto._

case class ItemValue(id: ItemId, values: List[MValue])

object ItemValue {
  implicit val ivCodec: Codec[ItemValue] = deriveCodec[ItemValue]

  def fromState(
      ranking: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      mapping: FeatureMapping
  ): List[ItemValue] = {

    val itemFeatures: List[ItemFeature] = mapping.features.collect { case feature: ItemFeature =>
      feature
    }

    val rankingFeatures = mapping.features.collect { case feature: RankingFeature =>
      feature
    }

    val rankingValues = rankingFeatures.map(_.value(ranking, state))

    val itemValuesMatrix = itemFeatures
      .map(feature => {
        val values = feature.values(ranking, state)
        values.foreach { value =>
          if (feature.dim != value.dim)
            throw new IllegalStateException(s"for ${feature.schema} dim mismatch: ${feature.dim} != ${value.dim}")
        }
        values
      })
      .transpose

    val itemScores = for {
      (item, itemValues) <- ranking.items.toList.zip(itemValuesMatrix)
    } yield {
      ItemValue(item.id, rankingValues ++ itemValues)
    }
    itemScores
  }
}
