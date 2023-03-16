package ai.metarank.ml.recommend.embedding

import ai.metarank.ml.recommend.embedding.HnswJavaIndex.{HnswIndexWriter, HnswOptions}
import ai.metarank.model.Identifier.ItemId
import cats.effect.unsafe.implicits.global
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
    val index   = HnswIndexWriter.write(map, HnswOptions(32, 200)).unsafeRunSync()
    val similar = index.lookup(List(ItemId("75")), 10).unsafeRunSync()
    similar.size shouldBe 10
  }

}
