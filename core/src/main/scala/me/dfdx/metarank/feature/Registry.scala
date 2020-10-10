package me.dfdx.metarank.feature

import me.dfdx.metarank.aggregation.{Aggregation, CountAggregation}
import me.dfdx.metarank.config.Config

case class Registry(aggs: List[Aggregation], features: List[Feature])

object Registry {
  def apply(config: Config) = {}
}
