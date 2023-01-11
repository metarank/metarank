package ai.metarank.model

import ai.metarank.model.FeatureValue.BoundedListValue.TimeValue
import java.util

sealed trait State {
  def key: Key
}

object State {
  case class ScalarState(key: Key, value: Scalar)                extends State
  case class MapState(key: Key, values: Map[String, Scalar])     extends State
  case class CounterState(key: Key, value: Int)                  extends State
  case class BoundedListState(key: Key, values: List[TimeValue]) extends State
  case class FreqEstimatorState(key: Key, values: List[String])  extends State
  case class StatsEstimatorState(key: Key, values: Array[Double]) extends State {
    override def equals(obj: Any): Boolean = obj match {
      case ss: StatsEstimatorState => (ss.key == key) && (util.Arrays.compare(ss.values, values) == 0)
      case _                       => false
    }
  }
  case class PeriodicCounterState(key: Key, values: Map[Timestamp, Long]) extends State
}
