package ai.metarank.ml.recommend

import ai.metarank.ml.{Predictor, PredictorSuite}
import ai.metarank.ml.recommend.TrendingRecommender.{
  InteractionWeight,
  TrendingConfig,
  TrendingItemScore,
  TrendingModel,
  TrendingPredictor
}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Timestamp
import ai.metarank.util.TestClickthroughValues
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration._

class TrendingRecommenderTest extends PredictorSuite[TrendingConfig, RecommendRequest, TrendingModel] {
  val conf = TrendingConfig(weights =
    List(
      InteractionWeight(interaction = "purchase", weight = 5.0, decay = 0.5),
      InteractionWeight(interaction = "click", weight = 1.0, decay = 0.5)
    )
  )
  override def predictor                         = TrendingPredictor("foo", conf)
  override def request(n: Int): RecommendRequest = RecommendRequest(count = 5)
  val now                                        = Timestamp.now

  it should "count clicks for today" in {
    val clicks = List(
      int("p1", "click"),
      int("p2", "click"),
      int("p3", "click"),
      int("p2", "click"),
      int("p2", "click")
    )
    val model = predictor.fit(fs2.Stream(clicks: _*)).unsafeRunSync()
    model.items.toList shouldBe List(
      TrendingItemScore(ItemId("p2"), 3.0),
      TrendingItemScore(ItemId("p1"), 1.0),
      TrendingItemScore(ItemId("p3"), 1.0)
    )
  }

  it should "decay for prev days" in {
    val clicks = List(
      int("p1", "click", now),
      int("p2", "click", now),
      int("p3", "click", now),
      int("p2", "click", now.minus(1.day))
    )
    val model = predictor.fit(fs2.Stream(clicks: _*)).unsafeRunSync()
    model.items.toList shouldBe List(
      TrendingItemScore(ItemId("p2"), 1.5),
      TrendingItemScore(ItemId("p1"), 1.0),
      TrendingItemScore(ItemId("p3"), 1.0)
    )
  }

  it should "combine by weight" in {
    val clicks = List(
      int("p1", "click", now),
      int("p2", "click", now),
      int("p3", "click", now),
      int("p2", "purchase", now)
    )
    val model = predictor.fit(fs2.Stream(clicks: _*)).unsafeRunSync()
    model.items.toList shouldBe List(
      TrendingItemScore(ItemId("p2"), 6.0),
      TrendingItemScore(ItemId("p1"), 1.0),
      TrendingItemScore(ItemId("p3"), 1.0)
    )
  }

  def int(id: String, tpe: String, ts: Timestamp = Timestamp.now) = {
    val t = TestClickthroughValues(List(id))
    t.copy(ct = t.ct.copy(ts = ts, interactions = t.ct.interactions.map(i => i.copy(tpe = tpe))))
  }
}
