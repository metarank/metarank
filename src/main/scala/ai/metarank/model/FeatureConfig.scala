package ai.metarank.model

import ai.metarank.model.Key._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

sealed trait FeatureConfig {
  def scope: Scope
  def name: FeatureName
  def ttl: FiniteDuration
  def refresh: FiniteDuration
  def monitorLag: Option[Boolean]
  def fqdn = s"${scope.name}.${name.value}"
}

object FeatureConfig {
  case class MonitorValuesConfig(min: Double, max: Double, buckets: Int) {
    lazy val bucketList = {
      val buffer = ArrayBuffer[Double]()
      val step   = (max - min) / buckets
      var i      = min
      while (i <= max) {
        buffer.append(i)
        i += step
      }
      buffer.toList
    }
  }

  case class CounterConfig(
      scope: Scope,
      name: FeatureName,
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorValues: Option[MonitorValuesConfig] = None,
      monitorLag: Option[Boolean] = None
  ) extends FeatureConfig

  case class ScalarConfig(
      scope: Scope,
      name: FeatureName,
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorValues: Option[MonitorValuesConfig] = None,
      monitorLag: Option[Boolean] = None
  ) extends FeatureConfig

  case class MapConfig(
      scope: Scope,
      name: FeatureName,
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorLag: Option[Boolean] = None,
      monitorValues: Option[MonitorValuesConfig] = None,
      monitorSize: Option[Boolean] = None
  ) extends FeatureConfig

  case class BoundedListConfig(
      scope: Scope,
      name: FeatureName,
      count: Int = Int.MaxValue,
      duration: FiniteDuration = Long.MaxValue.nanos,
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorLag: Option[Boolean] = None,
      monitorSize: Option[Boolean] = None
  ) extends FeatureConfig

  case class FreqEstimatorConfig(
      scope: Scope,
      name: FeatureName,
      poolSize: Int,
      sampleRate: Int,
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorLag: Option[Boolean] = None,
      monitorSize: Option[Boolean] = None
  ) extends FeatureConfig

  case class PeriodRange(startOffset: Int, endOffset: Int)
  case class PeriodicCounterConfig(
      scope: Scope,
      name: FeatureName,
      period: FiniteDuration,
      sumPeriodRanges: List[PeriodRange],
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorLag: Option[Boolean] = None,
      monitorValues: Option[MonitorValuesConfig] = None
  ) extends FeatureConfig {
    val periods: List[Int]   = (sumPeriodRanges.map(_.startOffset) ++ sumPeriodRanges.map(_.endOffset)).sorted
    val latestPeriodOffset   = periods.head
    val earliestPeriodOffset = periods.last
  }

  case class StatsEstimatorConfig(
      scope: Scope,
      name: FeatureName,
      poolSize: Int,
      sampleRate: Double,
      percentiles: List[Int],
      ttl: FiniteDuration = 365.days,
      refresh: FiniteDuration = 1.hour,
      monitorLag: Option[Boolean] = None,
      monitorValues: Option[MonitorValuesConfig] = None
  ) extends FeatureConfig

  case class ConfigParsingError(msg: String) extends Exception(msg)
}
