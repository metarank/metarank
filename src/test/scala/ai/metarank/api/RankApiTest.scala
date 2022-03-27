package ai.metarank.api

import ai.metarank.api.RankApiTest.BrokenStore
import ai.metarank.mode.inference.FeatureStoreResource
import ai.metarank.mode.inference.api.RankApi
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
  it should "handle protocol errors" in {
    val mapping = TestFeatureMapping()
    val store = Ref.of[IO, FeatureStoreResource](
      FeatureStoreResource(
        storeRef = Ref.of[IO, FeatureStore](new BrokenStore()).unsafeRunSync(),
        makeStore = () => RandomFeatureStore(mapping)
      )
    )
    val api       = RankApi(mapping, store.unsafeRunSync(), RandomScorer())
    val response1 = Try(api.rerank(TestRankingEvent(List("p1", "p2", "p3")), false).unsafeRunSync())
    response1.isFailure shouldBe false
  }
}

object RankApiTest {
  class BrokenStore extends FeatureStore {
    override def read(request: ReadRequest): IO[ReadResponse] = IO.raiseError(new IllegalArgumentException("oops"))
    override def write(batch: List[FeatureValue]): IO[Unit]   = IO.raiseError(new IllegalArgumentException("oops"))
    override def close(): IO[Unit]                            = IO.raiseError(new IllegalArgumentException("oops"))
  }
}
