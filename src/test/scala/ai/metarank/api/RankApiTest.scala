package ai.metarank.api

import ai.metarank.api.RankApiTest.{BrokenStore, CountingStore}
import ai.metarank.mode.inference.FeatureStoreResource
import ai.metarank.mode.inference.api.RankApi
import ai.metarank.mode.inference.api.RankApi.StateReadError
import ai.metarank.util.{RandomFeatureStore, RandomScorer, TestFeatureMapping, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import io.findify.featury.model.FeatureValue
import io.findify.featury.model.api.{ReadRequest, ReadResponse}
import io.findify.featury.values.FeatureStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class RankApiTest extends AnyFlatSpec with Matchers {
  val mapping = TestFeatureMapping()

  it should "unfold long requests" in {
    val counter = new CountingStore()
    val store = Ref.of[IO, FeatureStoreResource](
      FeatureStoreResource(
        storeRef = Ref.of[IO, FeatureStore](counter).unsafeRunSync(),
        makeStore = () => counter
      )
    )
    val api       = RankApi(mapping, store.unsafeRunSync(), Map("random" -> RandomScorer()))
    val request   = TestRankingEvent((0 until 1000).map(i => s"p$i").toList)
    val response1 = api.rerank(request, "random", false).unsafeRunSync()
    response1.items.size shouldBe 1000
    counter.reads shouldBe 12
    counter.featureReads shouldBe 6001
  }
  it should "handle protocol errors" in {
    val store = Ref.of[IO, FeatureStoreResource](
      FeatureStoreResource(
        storeRef = Ref.of[IO, FeatureStore](new BrokenStore()).unsafeRunSync(),
        makeStore = () => RandomFeatureStore(mapping)
      )
    )
    val api       = RankApi(mapping, store.unsafeRunSync(), Map("random" -> RandomScorer()))
    val response1 = api.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", false).unsafeRunSync()
    response1.items.size shouldBe 3
  }

  it should "fail request on broken state backend" in {
    val broken = new BrokenStore()
    val store = Ref.of[IO, FeatureStoreResource](
      FeatureStoreResource(
        storeRef = Ref.of[IO, FeatureStore](broken).unsafeRunSync(),
        makeStore = () => broken
      )
    )
    val api       = RankApi(mapping, store.unsafeRunSync(), Map("random" -> RandomScorer()))
    val response1 = Try(api.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", false).unsafeRunSync())
    response1.isFailure shouldBe true
  }
}

object RankApiTest {
  class BrokenStore extends FeatureStore {
    override def read(request: ReadRequest): IO[ReadResponse] = IO.raiseError(StateReadError("oops"))
    override def write(batch: List[FeatureValue]): IO[Unit]   = IO.raiseError(StateReadError("oops"))
    override def close(): IO[Unit]                            = IO.raiseError(StateReadError("oops"))
  }

  class CountingStore extends FeatureStore {
    var reads        = 0
    var featureReads = 0
    var writes       = 0
    override def read(request: ReadRequest): IO[ReadResponse] = IO {
      reads += 1
      featureReads += request.keys.size
      ReadResponse(Nil)
    }
    override def write(batch: List[FeatureValue]): IO[Unit] = IO {
      writes += 1
    }
    override def close(): IO[Unit] = IO.unit
  }
}
