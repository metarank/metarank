package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.semiauto._
import me.dfdx.metarank.model.Event.RankItem

case class RankRequest(id: RequestId, timestamp: Timestamp, scope: Scope, items: NonEmptyList[RankItem])

object RankRequest {
  implicit val requestCodec = deriveCodec[RankRequest]
}
