package ai.metarank.util

import ai.metarank.model.Identifier.ItemId
import org.apache.commons.math3.stat.correlation.KendallsCorrelation

object KendallCorrelation {
  def apply(a: List[ItemId], b: List[ItemId]): Double = {
    val indices = a.zipWithIndex.map { case (k, v) => k -> v.toDouble }.toMap
    val kendall = new KendallsCorrelation()
    val aind    = a.map(indices).toArray
    val bind    = b.map(indices).toArray
    kendall.correlation(aind, bind)
  }
}
