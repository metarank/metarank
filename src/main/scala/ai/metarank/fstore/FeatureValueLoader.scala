package ai.metarank.fstore

import ai.metarank.FeatureMapping
import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.{FeatureValue, Key}
import cats.effect.IO

object FeatureValueLoader {
  def fromStateBackend(mapping: FeatureMapping, ranking: RankingEvent, store: Persistence): IO[Map[Key, FeatureValue]] =
    for {
      keys1  <- IO { mapping.features.flatMap(_.valueKeys(ranking)) }
      state1 <- store.values.get(keys1)
      keys2  <- IO { mapping.features.flatMap(_.valueKeys2(ranking, state1)) }
      state2 <- store.values.get(keys2)
    } yield {
      state1 ++ state2
    }
}
