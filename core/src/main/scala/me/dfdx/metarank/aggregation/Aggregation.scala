package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.config.AggregationConfig
import me.dfdx.metarank.model.{Context, Event, ItemId}
import me.dfdx.metarank.store.Store

trait Aggregation {
  def onEvent(event: Event): IO[Unit]
}

object Aggregation {
  def fromConfig(store: Store, agg: AggregationConfig) = agg match {
    case AggregationConfig.CountAggregationConfig(daysBack) => CountAggregation(store, daysBack)
  }
}
