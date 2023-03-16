package ai.metaranke2e.knn

import ai.metarank.ml.recommend.KnnConfig.QdrantConfig
import ai.metarank.ml.recommend.embedding.{EmbeddingMap, KnnIndex}
import ai.metarank.model.Identifier.ItemId
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QdrantTest extends AnyFlatSpec with Matchers {
  it should "write embeddings" in {
    val map = EmbeddingMap(
      ids = Array("1", "2", "3"),
      embeddings = Array(
        Array(1.0, 2.0, 3.0),
        Array(1.0, 2.0, 1.0),
        Array(1.0, 1.0, 0.0)
      ),
      rows = 3,
      cols = 3
    )
    val store    = KnnIndex.write(map, QdrantConfig("http://localhost:6333", "test", 3, "Cosine")).unsafeRunSync()
    val response = store.lookup(List(ItemId("1")), 3).unsafeRunSync()
    response.map(_.item.value) shouldBe List("2", "3")
  }
}
