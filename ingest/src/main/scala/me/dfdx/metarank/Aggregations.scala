package me.dfdx.metarank

import cats.data.NonEmptyList
import cats.effect.IO
import me.dfdx.metarank.aggregation.Aggregation
import me.dfdx.metarank.config.Config.FeaturespaceConfig
import me.dfdx.metarank.model.Event
import me.dfdx.metarank.store.Store
import cats.implicits._

case class Aggregations(aggs: NonEmptyList[Aggregation]) {
  def onEvent(event: Event): IO[Unit] = aggs.map(_.onEvent(event)).sequence.map(_ => ())
}

object Aggregations {
  def fromConfig(fs: FeaturespaceConfig): Aggregations = {
    val store = Store.fromConfig(fs.store, fs.id)
    val aggs  = fs.aggregations.map(Aggregation.fromConfig(store, _))
    new Aggregations(aggs)
  }
}
