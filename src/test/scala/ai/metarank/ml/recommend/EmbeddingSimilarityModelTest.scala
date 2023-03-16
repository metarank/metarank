package ai.metarank.ml.recommend

import ai.metarank.ml.Model
import ai.metarank.ml.Model.ItemScore
import ai.metarank.ml.recommend.MFRecommender.EmbeddingSimilarityModel
import ai.metarank.ml.recommend.embedding.KnnIndex.KnnIndexReader
import ai.metarank.model.Identifier
import ai.metarank.model.Identifier.ItemId
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EmbeddingSimilarityModelTest extends AnyFlatSpec with Matchers {
  it should "not return the same item" in {
    val index = new KnnIndexReader {
      override def lookup(items: List[Identifier.ItemId], n: Int): IO[List[Model.ItemScore]] =
        IO.pure(
          List(
            ItemScore(ItemId("p1"), 1.0),
            ItemScore(ItemId("p2"), 1.0),
            ItemScore(ItemId("p3"), 1.0),
            ItemScore(ItemId("p4"), 1.0),
            ItemScore(ItemId("p5"), 1.0)
          )
        )

      override def save(): Array[Byte] = ???
    }
    val model    = EmbeddingSimilarityModel("foo", index)
    val response = model.predict(RecommendRequest(3, items = List(ItemId("p2"), ItemId("p4")))).unsafeRunSync()
    response.items.toList.map(_.item.value) shouldBe List("p1", "p3", "p5")
  }
}
