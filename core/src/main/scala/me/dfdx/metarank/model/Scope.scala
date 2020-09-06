package me.dfdx.metarank.model

import io.circe.generic.semiauto.deriveCodec

case class Scope(user: UserId, session: SessionId, fields: List[Field])

object Scope {
  implicit val scopeCodec = deriveCodec[Scope]
}
