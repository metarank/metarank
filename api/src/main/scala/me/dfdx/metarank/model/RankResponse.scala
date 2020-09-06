package me.dfdx.metarank.model

import cats.data.NonEmptyList
import me.dfdx.metarank.model.Event.RankItem
import io.circe.generic.semiauto._

case class RankResponse(items: NonEmptyList[RankItem])

object RankResponse {
  implicit val rankResponseCodec = deriveCodec[RankResponse]
}
