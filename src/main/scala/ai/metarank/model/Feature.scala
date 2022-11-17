package ai.metarank.model

import ai.metarank.model.Feature.BoundedListFeature.BoundedListConfig
import ai.metarank.model.Feature.CounterFeature.CounterConfig
import ai.metarank.model.Feature.FreqEstimatorFeature.FreqEstimatorConfig
import ai.metarank.model.Feature.MapFeature.MapConfig
import ai.metarank.model.Feature.PeriodicCounterFeature.PeriodicCounterConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.Feature.StatsEstimatorFeature.StatsEstimatorConfig
import ai.metarank.model.FeatureValue.PeriodicCounterValue.PeriodicValue
import ai.metarank.model.Write._
import ai.metarank.model.FeatureValue._
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.Scope.{GlobalScope, ItemScope, SessionScope, UserScope}
import cats.effect.IO
import com.google.common.math.Quantiles

import scala.concurrent.duration._
import scala.util.Random

sealed trait Feature[W <: Write, T <: FeatureValue] {
  def put(action: W): IO[Unit]
  def computeValue(key: Key, ts: Timestamp): IO[Option[T]]
}

object Feature {
  sealed trait FeatureConfig {
    def scope: ScopeType
    def name: FeatureName
    def ttl: FiniteDuration
    def refresh: FiniteDuration
    def readKeys(event: Event.RankingEvent): Iterable[Key] = scope match {
      case ScopeType.ItemScopeType    => event.items.toList.map(ir => Key(ItemScope(ir.id), name))
      case ScopeType.UserScopeType    => event.user.map(u => Key(UserScope(u), name))
      case ScopeType.SessionScopeType => event.session.map(s => Key(SessionScope(s), name))
      case ScopeType.GlobalScopeType  => Some(Key(GlobalScope, name))
    }
  }

  trait ScalarFeature extends Feature[Put, ScalarValue] {
    def config: ScalarConfig
  }

  object ScalarFeature {
    case class ScalarConfig(
        scope: ScopeType,
        name: FeatureName,
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig
  }

  trait MapFeature extends Feature[PutTuple, MapValue] {
    def config: MapConfig
  }

  object MapFeature {
    case class MapConfig(
        scope: ScopeType,
        name: FeatureName,
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig
  }

  trait CounterFeature extends Feature[Increment, CounterValue] {
    def config: CounterConfig
  }

  object CounterFeature {
    case class CounterConfig(
        scope: ScopeType,
        name: FeatureName,
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig

  }

  trait BoundedListFeature extends Feature[Append, BoundedListValue] {
    def config: BoundedListConfig
  }

  object BoundedListFeature {
    case class BoundedListConfig(
        scope: ScopeType,
        name: FeatureName,
        count: Int = Int.MaxValue,
        duration: FiniteDuration = Long.MaxValue.nanos,
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig
  }

  trait FreqEstimatorFeature extends Feature[PutFreqSample, FrequencyValue] {
    def config: FreqEstimatorConfig

    def freqFromSamples(samples: List[String]): Option[Map[String, Double]] = {
      if (samples.nonEmpty) {
        val sum = samples.size.toDouble
        val result = samples.groupBy(identity).map { case (key, values) =>
          key -> values.size / sum
        }
        Some(result)
      } else {
        None
      }
    }
  }

  object FreqEstimatorFeature {
    case class FreqEstimatorConfig(
        scope: ScopeType,
        name: FeatureName,
        poolSize: Int,
        sampleRate: Int,
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig

  }

  trait PeriodicCounterFeature extends Feature[PeriodicIncrement, PeriodicCounterValue] {
    def config: PeriodicCounterConfig
    def fromMap(map: Map[Timestamp, Long]): List[PeriodicValue] = {
      for {
        range         <- config.sumPeriodRanges
        lastTimestamp <- map.keys.toList.sortBy(_.ts).lastOption
      } yield {
        val start = lastTimestamp.minus(config.period * range.startOffset)
        val end   = lastTimestamp.minus(config.period * range.endOffset).plus(config.period)
        val sum =
          map.filter(ts => ts._1.isBeforeOrEquals(end) && ts._1.isAfterOrEquals(start)).values.toList match {
            case Nil      => 0L
            case nonEmpty => nonEmpty.sum
          }
        PeriodicValue(start, end, range.startOffset - range.endOffset + 1, sum)
      }
    }
  }

  object PeriodicCounterFeature {
    case class PeriodRange(startOffset: Int, endOffset: Int)

    case class PeriodicCounterConfig(
        scope: ScopeType,
        name: FeatureName,
        period: FiniteDuration,
        sumPeriodRanges: List[PeriodRange],
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig {
      val periods: List[Int]   = (sumPeriodRanges.map(_.startOffset) ++ sumPeriodRanges.map(_.endOffset)).sorted
      val latestPeriodOffset   = periods.head
      val earliestPeriodOffset = periods.last
    }

  }

  trait StatsEstimatorFeature extends Feature[PutStatSample, NumStatsValue] {
    def config: StatsEstimatorConfig
    import scala.jdk.CollectionConverters._
    def fromPool(key: Key, ts: Timestamp, pool: Seq[Double]): NumStatsValue = {
      val quantile = Quantiles
        .percentiles()
        .indexes(config.percentiles: _*)
        .compute(pool: _*)
        .asScala
        .map { case (k, v) =>
          k.intValue() -> v.doubleValue()
        }
      NumStatsValue(
        key = key,
        ts = ts,
        min = pool.min,
        max = pool.max,
        quantiles = quantile.toMap
      )
    }
  }

  object StatsEstimatorFeature {
    case class StatsEstimatorConfig(
        scope: ScopeType,
        name: FeatureName,
        poolSize: Int,
        sampleRate: Double,
        percentiles: List[Int],
        ttl: FiniteDuration = 365.days,
        refresh: FiniteDuration = 1.hour
    ) extends FeatureConfig

  }

  def shouldSample(rate: Double): Boolean = Random.nextDouble() <= rate
}
