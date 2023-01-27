package ai.metarank.ml.recommend

import ai.metarank.ml.PredictorSuite
import ai.metarank.ml.recommend.MFRecommender.{MFModel, MFPredictor}
import ai.metarank.ml.recommend.mf.ALSRecImpl
import ai.metarank.ml.recommend.mf.ALSRecImpl.ALSConfig
import ai.metarank.ml.recommend.mf.MFRecImpl.MFModelConfig
import ai.metarank.model.ClickthroughValues
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.TestClickthroughValues

import scala.util.Random

class MFRecommenderTest extends PredictorSuite[MFModelConfig, RecommendRequest, MFModel] {
  val conf               = ALSConfig()

  override def predictor = MFPredictor("foo", conf, ALSRecImpl(conf))

  override def request(n: Int): RecommendRequest = RecommendRequest(items = List(ItemId("p10")), count = 10)

  override def cts: List[ClickthroughValues] = (0 until 1000)
    .map(_ =>
      TestClickthroughValues.random(
        List(
          "p" + Random.nextInt(100).toString,
          "p" + Random.nextInt(100).toString,
          "p" + Random.nextInt(100).toString
        )
      )
    )
    .toList

}
