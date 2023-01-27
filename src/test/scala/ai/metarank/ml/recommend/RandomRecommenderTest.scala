package ai.metarank.ml.recommend

import ai.metarank.ml.PredictorSuite
import ai.metarank.ml.recommend.RandomRecommender.{RandomConfig, RandomModel, RandomPredictor}
import cats.effect.unsafe.implicits.global

class RandomRecommenderTest extends PredictorSuite[RandomConfig, RecommendRequest, RandomModel] {
  override def predictor       = RandomPredictor("yolo", RandomConfig())
  override def request(n: Int) = RecommendRequest(n)

  it should "return all items for large N" in {
    val rec      = predictor.fit(fs2.Stream(cts: _*)).unsafeRunSync()
    val items    = cts.flatMap(_.ct.items).distinct
    val response = rec.predict(RecommendRequest(items.size * 2)).unsafeRunSync()
    response.items.size shouldBe items.size
  }

  it should "return subsample for small N" in {
    val rec      = predictor.fit(fs2.Stream(cts: _*)).unsafeRunSync()
    val response = rec.predict(RecommendRequest(2)).unsafeRunSync()
    response.items.size shouldBe 2
  }
}
