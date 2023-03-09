package ai.metarank.feature

import ai.metarank.FeatureMapping
import ai.metarank.config.BoosterConfig.LightGBMConfig
import ai.metarank.feature.BaseFeature.ValueMode
import ai.metarank.flow.FeatureValueFlow
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.ml.rank.LambdaMARTRanker.LambdaMARTConfig
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{Event, FeatureSchema, MValue}
import cats.data.{NonEmptyList, NonEmptyMap}
import cats.effect.unsafe.implicits.global
import com.github.blemale.scaffeine.Scaffeine
import fs2.Stream

trait FeatureTest {
  def process(events: List[Event], schema: FeatureSchema, request: RankingEvent): List[List[MValue]] = {
    val mapping = FeatureMapping.fromFeatureSchema(
      schema = List(schema),
      models = Map("random" -> LambdaMARTConfig(LightGBMConfig(), NonEmptyList.of(schema.name), Map("click" -> 1)))
    )

    val flow =
      FeatureValueFlow(mapping, MemPersistence(mapping.schema), Scaffeine().maximumSize(0).build())
    val featureValues =
      Stream
        .emits(events)
        .through(flow.process)
        .compile
        .toList
        .map(_.flatten)
        .unsafeRunSync()
        .map(fv => fv.key -> fv)
        .toMap

    mapping.features.map {
      case feature: BaseFeature.ItemFeature    => feature.values(request, featureValues, ValueMode.OfflineTraining)
      case feature: BaseFeature.RankingFeature => List(feature.value(request, featureValues))
    }
  }

}
