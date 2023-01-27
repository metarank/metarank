package ai.metarank.ml.recommend

import ai.metarank.ml.Context
import ai.metarank.model.Identifier
import ai.metarank.model.Identifier.{ItemId, SessionId, UserId}
import io.circe.Decoder

case class RecommendRequest(count: Int, user: Option[UserId] = None, items: List[ItemId] = Nil) extends Context {}

object RecommendRequest {
  implicit val recommendRequestDecoder: Decoder[RecommendRequest] = Decoder.instance(c =>
    for {
      count <- c.downField("count").as[Int]
      user  <- c.downField("user").as[Option[UserId]]
      items <- c.downField("items").as[Option[List[ItemId]]]
    } yield {
      RecommendRequest(count, user, items.getOrElse(Nil))
    }
  )
}
