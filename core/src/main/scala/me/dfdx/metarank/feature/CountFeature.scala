package me.dfdx.metarank.feature

import cats.effect.IO
import cats.implicits._
import me.dfdx.metarank.aggregation.Scope.{ClickType, ConversionType, ItemContextScope, ItemScope}
import me.dfdx.metarank.aggregation.{CountAggregation, Scope}
import me.dfdx.metarank.config.FeatureConfig.CountFeatureConfig
import me.dfdx.metarank.model.Event.{RankEvent, RankItem}

case class CountFeature(counts: CountAggregation, conf: CountFeatureConfig) extends Feature {
  override def values(event: RankEvent, item: RankItem): IO[List[Float]] = {
    val scopes = List(
      ItemScope(ClickType, item.id),
      ItemContextScope(ClickType, item.id, event.context),
      ItemScope(ConversionType, item.id),
      ItemContextScope(ConversionType, item.id, event.context)
    )
    scopes.map(values).sequence.map(_.flatten)
  }

  private def values(scope: Scope) = for {
    count <- counts.store.value(counts.reservoir, scope).get().map(_.getOrElse(counts.reservoir.default))
  } yield {
    val s   = scope
    val res = conf.windows.map(w => count.sum(w.from, w.length).toFloat).toList
    val br  = 1
    res
  }
}
