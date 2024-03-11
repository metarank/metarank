package ai.metarank.ml

import ai.metarank.ml.Model.Response
import ai.metarank.ml.rank.{LambdaMARTRanker, NoopRanker, QueryRequest, RankRequest, ShuffleRanker}
import ai.metarank.ml.recommend.{RandomRecommender, RecommendRequest}
import ai.metarank.model.Identifier.ItemId
import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.Encoder

sealed trait Model[T <: Context] {
  def name: String
  def save(): Option[Array[Byte]]
  def predict(request: T): IO[Response]
  def close(): Unit       = {}
  def isClosed(): Boolean = false
}

object Model {
  case class ItemScore(item: ItemId, score: Double)
  case class Response(items: NonEmptyList[ItemScore])

  trait RecommendModel extends Model[RecommendRequest]
  trait RankModel      extends Model[QueryRequest]
}
