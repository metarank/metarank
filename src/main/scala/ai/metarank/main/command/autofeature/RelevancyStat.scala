package ai.metarank.main.command.autofeature

import ai.metarank.model.Event.{ItemRelevancy, RankingEvent}

case class RelevancyStat(nonZero: Int = 0, min: Option[Double] = None, max: Option[Double] = None) {
  def refresh(rank: RankingEvent): RelevancyStat = rank.items.toList.foldLeft(this)((acc, next) => acc.refresh(next))

  def refresh(item: ItemRelevancy): RelevancyStat = RelevancyStat(
    nonZero = if (item.relevancy.contains(0.0)) nonZero else nonZero + 1,
    min = min(min, item.relevancy),
    max = max(max, item.relevancy)
  )

  def min(a: Option[Double], b: Option[Double]): Option[Double] = (a, b) match {
    case (Some(a), Some(b)) => if (a < b) Some(a) else Some(b)
    case (Some(a), None)    => Some(a)
    case (None, Some(b))    => Some(b)
    case (None, None)       => None
  }

  def max(a: Option[Double], b: Option[Double]): Option[Double] = (a, b) match {
    case (Some(a), Some(b)) => if (a > b) Some(a) else Some(b)
    case (Some(a), None)    => Some(a)
    case (None, Some(b))    => Some(b)
    case (None, None)       => None
  }
}
