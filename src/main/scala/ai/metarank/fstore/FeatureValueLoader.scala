package ai.metarank.fstore

import ai.metarank.FeatureMapping
import ai.metarank.fstore.Persistence.KVStore
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{FeatureValue, Key}
import cats.effect.IO

object FeatureValueLoader {
  def fromStateBackend(
      mapping: FeatureMapping,
      ranking: RankingEvent,
      values: KVStore[Key, FeatureValue],
      subset: Set[FeatureName]
  ): IO[Map[Key, FeatureValue]] =
    for {
      modelFeatures <- IO { mapping.features.filter(f => subset.contains(f.schema.name)) }
      keys1         <- IO { modelFeatures.flatMap(_.valueKeys(ranking)) }
      state1        <- values.get(keys1)
      keys2         <- IO { modelFeatures.flatMap(_.valueKeys2(ranking, state1)) }
      state2        <- values.get(keys2)
    } yield {
      state1 ++ state2
    }
}
