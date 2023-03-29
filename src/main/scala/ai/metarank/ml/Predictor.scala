package ai.metarank.ml

import ai.metarank.config.ModelConfig
import ai.metarank.ml.Model.{RankModel, RecommendModel}
import ai.metarank.ml.rank.{QueryRequest, RankRequest}
import ai.metarank.ml.recommend.RecommendRequest
import ai.metarank.model.TrainValues
import cats.effect.IO

sealed trait Predictor[C <: ModelConfig, T <: Context, M <: Model[T]] {
  def config: C
  def name: String
  def fit(data: fs2.Stream[IO, TrainValues]): IO[M]
  def load(bytes: Option[Array[Byte]]): IO[M]
}

object Predictor {
  trait RecommendPredictor[C <: ModelConfig, M <: RecommendModel] extends Predictor[C, RecommendRequest, M]
  trait RankPredictor[C <: ModelConfig, M <: RankModel]           extends Predictor[C, QueryRequest, M]

  case class EmptyDatasetException(msg: String) extends Exception(msg)
}
