package me.dfdx.metarank.feature

import cats.effect.IO
import me.dfdx.metarank.model.Event.{RankEvent, RankItem}

trait Feature {
  def values(event: RankEvent, item: RankItem): IO[List[Float]]
}
