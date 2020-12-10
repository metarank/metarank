package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.config.AggregationConfig
import me.dfdx.metarank.config.Config.SchemaConfig
import me.dfdx.metarank.model.{Context, Event, ItemId}
import me.dfdx.metarank.store.Store

trait Aggregation {
  def onEvent(event: Event): IO[Unit]
}

object Aggregation {
  def fromConfig(store: Store, agg: AggregationConfig, schema: SchemaConfig): Aggregation = agg match {
    case AggregationConfig.CountAggregationConfig(daysBack) => CountAggregation(store, daysBack)
    case AggregationConfig.ItemMetadataAggregationConfig()  => ItemMetadataAggregation(store, schema)
  }
}
