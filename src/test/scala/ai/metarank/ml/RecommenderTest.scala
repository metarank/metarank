package ai.metarank.ml

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.ml.recommend.RandomRecommender.{RandomConfig, RandomModel, RandomPredictor}
import ai.metarank.ml.recommend.RecommendRequest
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.TestFeatureMapping
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RecommenderTest extends AnyFlatSpec with Matchers {
  it should "recommend things" in {
    val mapping = TestFeatureMapping().copy(models = Map("rec" -> RandomPredictor("rec", RandomConfig())))
    val store   = MemPersistence(mapping.schema)
    store.models.put(RandomModel("rec", Array(ItemId("p1"), ItemId("p2"), ItemId("p3"), ItemId("p4")))).unsafeRunSync()
    val rec = Recommender(mapping, store)

    val response = rec.recommend(RecommendRequest(10), "rec").unsafeRunSync()
    response.items.size shouldBe 4
    response.items.sortBy(-_.score) shouldBe response.items
  }
}
