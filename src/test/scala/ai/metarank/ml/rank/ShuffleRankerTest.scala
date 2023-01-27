package ai.metarank.ml.rank

import ai.metarank.ml.{Predictor, PredictorSuite}
import ai.metarank.ml.rank.ShuffleRanker.{ShuffleConfig, ShuffleModel, ShufflePredictor}
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Timestamp
import ai.metarank.util.TestQueryRequest
import cats.effect.unsafe.implicits.global
import io.github.metarank.ltrlib.model.Query

class ShuffleRankerTest extends PredictorSuite[ShuffleConfig, QueryRequest, ShuffleModel] {
  override def predictor: Predictor[ShuffleConfig, QueryRequest, ShuffleModel] =
    ShufflePredictor("foo", ShuffleConfig(2))

  override def request(n: Int): QueryRequest = TestQueryRequest(n)

  lazy val rec = predictor.fit(fs2.Stream(cts: _*)).unsafeRunSync()

  it should "return same items" in {
    val r      = request(100)
    val result = rec.predict(r).unsafeRunSync()
    result.items.map(_.item).toList.toSet shouldBe r.items.map(_.id).toList.toSet
  }

  it should "order is different" in {
    val r      = request(100)
    val result = rec.predict(r).unsafeRunSync()
    result.items.sortBy(_.score).map(_.item) shouldNot be(r.items.map(_.id))
  }
}
