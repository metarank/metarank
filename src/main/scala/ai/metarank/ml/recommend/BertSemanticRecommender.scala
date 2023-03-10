package ai.metarank.ml.recommend

import ai.metarank.config.{ModelConfig, Selector}
import ai.metarank.ml.Model
import ai.metarank.ml.Model.RecommendModel
import ai.metarank.ml.Predictor.RecommendPredictor
import ai.metarank.ml.recommend.MFRecommender.EmbeddingSimilarityModel
import ai.metarank.model.TrainValues
import ai.metarank.model.Key.FeatureName
import cats.effect.IO

object BertSemanticRecommender {
  case class BertSemanticPredictor(name: String, config: BertSemanticModelConfig)
      extends RecommendPredictor[BertSemanticModelConfig, EmbeddingSimilarityModel] {
    override def fit(data: fs2.Stream[IO, TrainValues]): IO[EmbeddingSimilarityModel] = ???

    override def load(bytes: Option[Array[Byte]]): Either[Throwable, EmbeddingSimilarityModel] = ???
  }

  case class BertSemanticModelConfig(selector: Selector, m: Int, ef: Int, encoder: FeatureName) extends ModelConfig
}
