package ai.metarank.model

import ai.metarank.config.EnvConfig
import ai.metarank.model.Feature.BoundedList.BoundedListConfig
import ai.metarank.model.Feature.Counter.CounterConfig
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.FreqEstimator.FreqEstimatorConfig
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.Feature.PeriodicCounter.PeriodicCounterConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.Feature.StatsEstimator.StatsEstimatorConfig
import ai.metarank.model.Key.FeatureName

case class Schema(
    counters: Map[FeatureKey, CounterConfig],
    scalars: Map[FeatureKey, ScalarConfig],
    periodicCounters: Map[FeatureKey, PeriodicCounterConfig],
    freqs: Map[FeatureKey, FreqEstimatorConfig],
    stats: Map[FeatureKey, StatsEstimatorConfig],
    lists: Map[FeatureKey, BoundedListConfig],
    maps: Map[FeatureKey, MapConfig],
    configs: Map[FeatureKey, FeatureConfig]
) {
  def merge(other: Schema): Schema = Schema(
    counters = counters ++ other.counters,
    scalars = scalars ++ other.scalars,
    periodicCounters = periodicCounters ++ other.periodicCounters,
    freqs = freqs ++ other.freqs,
    stats = stats ++ other.stats,
    lists = lists ++ other.lists,
    maps = maps ++ other.maps,
    configs = configs ++ other.configs
  )
}

object Schema {

  def apply(env: Env, features: List[FeatureConfig]): Schema = {
    val configs = for {
      c <- features
    } yield {
      FeatureKey(env, c.scope, c.name) -> c
    }
    new Schema(
      counters = configs.collect { case (key, c: CounterConfig) => key -> c }.toMap,
      scalars = configs.collect { case (key, c: ScalarConfig) => key -> c }.toMap,
      periodicCounters = configs.collect { case (key, c: PeriodicCounterConfig) => key -> c }.toMap,
      freqs = configs.collect { case (key, c: FreqEstimatorConfig) => key -> c }.toMap,
      stats = configs.collect { case (key, c: StatsEstimatorConfig) => key -> c }.toMap,
      lists = configs.collect { case (key, c: BoundedListConfig) => key -> c }.toMap,
      maps = configs.collect { case (key, c: MapConfig) => key -> c }.toMap,
      configs = configs.toMap
    )
  }
}
