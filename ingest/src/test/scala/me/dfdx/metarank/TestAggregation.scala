package me.dfdx.metarank

import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.model.Event

object TestAggregation extends Aggregation {
  def onEvent(event: Event): IO[Unit] = IO.unit
}
