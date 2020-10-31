package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.store.Store

case class ItemMetadataAggregation(store: Store) extends Aggregation {
  def onEvent(event: Event): IO[Unit] = ???
}
