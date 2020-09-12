package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.config.Config
import me.dfdx.metarank.config.Config.FeatureConfig
import me.dfdx.metarank.store.Store
import me.dfdx.metarank.tracker.{Aggregation, WindowCountAggregation}
import me.dfdx.metarank.tracker.state.CircularReservoir

class WindowCountFeature extends Feature {
  override def values(conf: FeatureConfig, scope: Aggregation.Scope, store: Store): IO[List[Float]] = {
    for {
      countsOption <- store.load[CircularReservoir](WindowCountAggregation, scope)
    } yield {
      val counts = countsOption.getOrElse(CircularReservoir(conf.maxDays))
      conf.windows.map(w => counts.sum(w.from, w.length).toFloat).toList
    }
  }
}
