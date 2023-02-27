package ai.metarank.model

import ai.metarank.FeatureMapping
import ai.metarank.feature.BaseFeature.{ItemFeature, RankingFeature, ValueMode}
import ai.metarank.model.Identifier.ItemId
import cats.data.NonEmptyList
import io.circe.Codec
import io.circe.generic.semiauto._

case class ItemValue(id: ItemId, values: List[MValue]) {
  // as an optimization
  override def hashCode(): Int = {
    var valuesHashCode = 0
    values.foreach(mv => {
      valuesHashCode = valuesHashCode ^ mv.hashCode()
    })
    valuesHashCode ^ id.value.hashCode
  }
}

object ItemValue {
  implicit val ivCodec: Codec[ItemValue] = deriveCodec[ItemValue]

  def fromState(
      ranking: Event.RankingEvent,
      state: Map[Key, FeatureValue],
      mapping: FeatureMapping,
      mode: ValueMode
  ): Either[Throwable, NonEmptyList[ItemValue]] = {

    val itemFeatures: List[ItemFeature] = mapping.features.collect { case feature: ItemFeature =>
      feature
    }

    val rankingFeatures = mapping.features.collect { case feature: RankingFeature =>
      feature
    }

    val rankingValues = rankingFeatures.map(feature => {
      val value = feature.value(ranking, state)
      if (feature.dim != value.dim)
        throw new IllegalStateException(s"for ${feature.schema} dim mismatch: ${feature.dim} != ${value.dim}")
      value
    })

    val itemValuesMatrix = itemFeatures
      .map(feature => {
        val values = feature.values(ranking, state, mode)
        values.foreach { value =>
          if (feature.dim != value.dim)
            throw new IllegalStateException(s"for ${feature.schema} dim mismatch: ${feature.dim} != ${value.dim}")
        }
        values
      })
      .transpose

    itemValuesMatrix match {
      case head :: tail =>
        val result = ranking.items.zip(NonEmptyList.of(head, tail: _*)).map { case (item, itemValues) =>
          ItemValue(item.id, rankingValues ++ itemValues)
        }
        Right(result)
      case _ => Left(new Exception("empty feature set"))
    }
  }
}
