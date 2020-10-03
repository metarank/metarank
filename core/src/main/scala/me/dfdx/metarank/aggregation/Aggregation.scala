package me.dfdx.metarank.aggregation

import cats.effect.IO
import me.dfdx.metarank.model.{Context, Event, ItemId}
import me.dfdx.metarank.store.Store

trait Aggregation {
  def onEvent(event: Event): IO[Unit]
}
