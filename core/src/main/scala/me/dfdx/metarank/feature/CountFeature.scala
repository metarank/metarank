package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.aggregation.{Aggregation, CircularReservoir, CountAggregation}
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig
import me.dfdx.metarank.store.state.StateDescriptor.ValueStateDescriptor

case class CountFeature(countAggregation: CountAggregation, conf: CountFeatureConfig) extends Feature {
  val reservoir = ValueStateDescriptor[CircularReservoir]("count")

  override def values(scope: Aggregation.Scope, store: Store): IO[List[Float]] = {
    for {
      countsOption <- store.value(reservoir, scope).get()
    } yield {
      val counts = countsOption.getOrElse(CircularReservoir(conf.maxDate))
      conf.windows.map(w => counts.sum(w.from, w.length).toFloat).toList
    }
  }
}
