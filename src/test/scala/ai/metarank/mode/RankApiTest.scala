package ai.metarank.mode

import ai.metarank.FeatureMapping
import ai.metarank.mode.standalone.FeatureStoreResource
import ai.metarank.mode.standalone.api.RankApi
import ai.metarank.util.{RandomFeatureStore, RandomScorer, TestFeatureMapping, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class RankApiTest extends AnyFlatSpec with Matchers {
  lazy val random  = new Random(1)
  lazy val mapping = TestFeatureMapping()
  lazy val store   = FeatureStoreResource.unsafe(() => RandomFeatureStore(mapping)).unsafeRunSync()
  lazy val service = RankApi(mapping, store, Map("random" -> RandomScorer()))

  it should "respond with the same data reranked" in {
    val response = service.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", explain = false).unsafeRunSync()
    response.items.map(_.item.value) shouldBe List("p1", "p3", "p2")
  }

  it should "emit feature values" in {
    val response = service.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", explain = true).unsafeRunSync()
    response.items.forall(_.features.size == 5) shouldBe true
  }

  it should "emit state" in {
    val response = service.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", explain = true).unsafeRunSync()
    response.state.session.size shouldBe 1
  }
}
