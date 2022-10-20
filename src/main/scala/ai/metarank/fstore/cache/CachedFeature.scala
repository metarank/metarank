package ai.metarank.fstore.cache

import ai.metarank.model.Feature._
import ai.metarank.model.FeatureValue.{
  BoundedListValue,
  CounterValue,
  FrequencyValue,
  MapValue,
  NumStatsValue,
  PeriodicCounterValue,
  ScalarValue
}
import ai.metarank.model.Write.{Append, Increment, PeriodicIncrement, Put, PutFreqSample, PutStatSample, PutTuple}
import ai.metarank.model._
import ai.metarank.util.Logging
import cats.effect.IO

sealed trait CachedFeature[W <: Write, T <: FeatureValue, F <: Feature[W, T]] extends Logging {
  def fast: F
  def slow: F
  def put(action: W): IO[Unit] = fast.put(action) *> slow.put(action)

  def computeValue(key: Key, ts: Timestamp): IO[Option[T]] =
    fast.computeValue(key, ts).flatMap {
      case None        => slow.computeValue(key, ts)
      case Some(value) => IO.pure(Some(value))
    }
}

object CachedFeature {
  case class CachedScalarFeature(fast: ScalarFeature, slow: ScalarFeature)
      extends CachedFeature[Put, ScalarValue, ScalarFeature]
      with ScalarFeature {
    def config = fast.config
  }

  case class CachedBoundedListFeature(fast: BoundedListFeature, slow: BoundedListFeature)
      extends CachedFeature[Append, BoundedListValue, BoundedListFeature]
      with BoundedListFeature {
    def config = fast.config
  }

  case class CachedCounterFeature(fast: CounterFeature, slow: CounterFeature)
      extends CachedFeature[Increment, CounterValue, CounterFeature]
      with CounterFeature {
    def config = fast.config
  }

  case class CachedFreqEstimatorFeature(fast: FreqEstimatorFeature, slow: FreqEstimatorFeature)
      extends CachedFeature[PutFreqSample, FrequencyValue, FreqEstimatorFeature]
      with FreqEstimatorFeature {
    def config = fast.config
  }

  case class CachedMapFeature(fast: MapFeature, slow: MapFeature)
      extends CachedFeature[PutTuple, MapValue, MapFeature]
      with MapFeature {
    def config = fast.config
  }

  case class CachedPeriodicCounterFeature(fast: PeriodicCounterFeature, slow: PeriodicCounterFeature)
      extends CachedFeature[PeriodicIncrement, PeriodicCounterValue, PeriodicCounterFeature]
      with PeriodicCounterFeature {
    def config = fast.config
  }

  case class CachedStatsEstimatorFeature(fast: StatsEstimatorFeature, slow: StatsEstimatorFeature)
      extends CachedFeature[PutStatSample, NumStatsValue, StatsEstimatorFeature]
      with StatsEstimatorFeature {
    def config = fast.config
  }
}
