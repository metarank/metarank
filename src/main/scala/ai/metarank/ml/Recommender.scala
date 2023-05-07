package ai.metarank.ml

import ai.metarank.FeatureMapping
import ai.metarank.api.routes.RankApi.RankResponse.ItemScoreValues
import ai.metarank.api.routes.RankApi.{ModelError, RankResponse}
import ai.metarank.config.ModelConfig
import ai.metarank.fstore.Persistence
import ai.metarank.fstore.Persistence.ModelName
import ai.metarank.ml.Model.RecommendModel
import ai.metarank.ml.Predictor.{RankPredictor, RecommendPredictor}
import ai.metarank.ml.recommend.RandomRecommender.{RandomModel, RandomPredictor}
import ai.metarank.ml.recommend.RecommendRequest
import ai.metarank.ml.recommend.TrendingRecommender.{TrendingModel, TrendingPredictor}
import ai.metarank.util.Logging
import cats.effect.IO

case class Recommender(mapping: FeatureMapping, store: Persistence) extends Logging {
  def recommend(request: RecommendRequest, modelName: String): IO[RankResponse] = for {
    start <- IO(System.currentTimeMillis())
    predictor <- mapping.models.get(modelName) match {
      case Some(existing: RecommendPredictor[_, _]) => IO.pure(existing)
      case Some(_: RankPredictor[_, _]) =>
        IO.raiseError(ModelError(s"cannot recommend over rank model $modelName"))
      case None => IO.raiseError(ModelError(s"model $modelName is not configured"))
    }

    model  <- loadModel(predictor, modelName)
    scores <- model.predict(request)
    total  <- IO(System.currentTimeMillis() - start)
    _      <- info(s"response: items=${scores.items.toList.map(_.item.value)} total=${total}ms")
  } yield {
    RankResponse(
      state = None,
      items = scores.items.toList.sortBy(-_.score).map(is => ItemScoreValues(is.item, is.score, features = None)),
      took = total
    )
  }

  def loadModel(pred: RecommendPredictor[_ <: ModelConfig, _ <: RecommendModel], name: String): IO[RecommendModel] = {
    store.models.get(ModelName(name), pred).flatMap {
      case Some(s: RecommendModel) => IO.pure(s)
      case Some(other)             => IO.raiseError(ModelError(s"model $name has wrong type $other"))
      case None                    => IO.raiseError(ModelError(s"model scorer $name is not yet trained"))
    }

  }

}
