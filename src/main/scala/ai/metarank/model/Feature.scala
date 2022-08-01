package ai.metarank.model

import ai.metarank.model.FeatureConfig._
import ai.metarank.model.FeatureValue.PeriodicCounterValue.PeriodicValue
import ai.metarank.model.Write._
import ai.metarank.model.FeatureValue._
import ai.metarank.model.State._
import com.google.common.math.Quantiles

import scala.util.Random

sealed trait Feature[W <: Write, T <: FeatureValue, C <: FeatureConfig, S <: State] {
  def put(action: W): Unit
  def config: C
  def computeValue(key: Key, ts: Timestamp): Option[T]
  def readState(key: Key, ts: Timestamp): Option[S]
  def writeState(state: S): Unit
}

object Feature {
  trait ScalarFeature extends Feature[Put, ScalarValue, ScalarConfig, ScalarState]

  trait MapFeature extends Feature[PutTuple, MapValue, MapConfig, MapState]

  trait Counter extends Feature[Increment, CounterValue, CounterConfig, CounterState]

  trait BoundedList extends Feature[Append, BoundedListValue, BoundedListConfig, BoundedListState]

  trait FreqEstimator extends Feature[PutFreqSample, FrequencyValue, FreqEstimatorConfig, FrequencyState] {
    override def put(action: PutFreqSample): Unit =
      if (Feature.shouldSample(config.sampleRate)) putSampled(action)
    def putSampled(action: PutFreqSample): Unit

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

  trait PeriodicCounter
      extends Feature[PeriodicIncrement, PeriodicCounterValue, PeriodicCounterConfig, PeriodicCounterState] {
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

  trait StatsEstimator extends Feature[PutStatSample, NumStatsValue, StatsEstimatorConfig, StatsState] {
    import scala.jdk.CollectionConverters._
    override def put(action: PutStatSample): Unit =
      if (Feature.shouldSample(config.sampleRate)) putSampled(action)
    def putSampled(action: PutStatSample): Unit
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

  def shouldSample(rate: Double): Boolean = Random.nextDouble() <= rate
}
