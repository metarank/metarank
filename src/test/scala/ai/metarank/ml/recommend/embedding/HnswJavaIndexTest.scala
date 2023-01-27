package ai.metarank.ml.recommend.embedding

import ai.metarank.model.Identifier.ItemId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class HnswJavaIndexTest extends AnyFlatSpec with Matchers {
  it should "build index" in {
    val source = (0 until 1000).map(i => Embedding(i.toString, Array.fill(100) { Random.nextDouble() })).toList
    val map = EmbeddingMap(
      ids = source.map(_.id).toArray,
      embeddings = source.map(e => e.vector).toArray,
      rows = 1000,
      cols = 100
    )
    val index   = HnswJavaIndex.create(map, 32, 200)
    val similar = index.lookup(List(ItemId("75")), 10)
    similar.size shouldBe 10
  }

}
