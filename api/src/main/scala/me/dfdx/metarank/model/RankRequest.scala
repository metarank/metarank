package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.semiauto._
import me.dfdx.metarank.model.Event.RankItem
import me.dfdx.metarank.model.RankRequest.Scope

case class RankRequest(id: RequestId, timestamp: Timestamp, scope: Scope, items: NonEmptyList[RankItem])

object RankRequest {
  case class Scope(user: UserId, session: SessionId, fields: List[Field])

  implicit val scopeCodec   = deriveCodec[Scope]
  implicit val requestCodec = deriveCodec[RankRequest]
}
