package ai.metarank.ml.rank

import ai.metarank.ml.{Predictor, PredictorSuite}
import ai.metarank.ml.rank.NoopRanker.{NoopConfig, NoopModel, NoopPredictor}
import ai.metarank.model.Event.RankItem
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.TestQueryRequest
import cats.data.NonEmptyList
import cats.effect.unsafe.implicits.global

class NoopRankerTest extends PredictorSuite[NoopConfig, QueryRequest, NoopModel] {
  override def predictor: Predictor[NoopConfig, QueryRequest, NoopModel] = NoopPredictor("foo", NoopConfig())

  override def request(n: Int): QueryRequest = TestQueryRequest(n)

  it should "return the same items" in {
    val rec    = predictor.fit(fs2.Stream(cts: _*)).unsafeRunSync()
    val r      = request(10)
    val result = rec.predict(r).unsafeRunSync()
    result.items.map(_.item) shouldBe r.items.map(_.id)
  }
}
