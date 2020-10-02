package me.dfdx.metarank.model

import cats.data.NonEmptyList
import io.circe.generic.semiauto._
import me.dfdx.metarank.model.Event.{Metadata, RankItem}

case class RankRequest(id: RequestId, metadata: Metadata, items: NonEmptyList[RankItem])

object RankRequest {
  implicit val requestCodec = deriveCodec[RankRequest]
}
