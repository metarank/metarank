package ai.metarank.main.api

import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.RankResponse
import ai.metarank.util.{RandomScorer, TestFeatureMapping, TestModelCache, TestRankingEvent}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.http4s.{Entity, Method, Request, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._

class RankApiTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val store   = MemPersistence(mapping.schema)
  lazy val models  = TestModelCache(RandomScorer())
  lazy val service = RankApi(mapping, store, models)

  it should "respond with the same data reranked" in {
    val response =
      service.rerank(mapping, TestRankingEvent(List("p1", "p2", "p3")), "random", explain = false).unsafeRunSync()
    response.items.map(_.item.value) shouldBe List("p1", "p3", "p2")
  }

  it should "emit feature values" in {
    val response =
      service.rerank(mapping, TestRankingEvent(List("p1", "p2", "p3")), "random", explain = true).unsafeRunSync()
    response.items.forall(_.features.size == 5) shouldBe true
  }

  it should "accept ranking json event" in {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/rank/random"),
      entity = Entity.strict(Chunk.array(TestRankingEvent.event(List("p1", "p2", "p3")).asJson.noSpaces.getBytes))
    )
    val response = service.routes.apply(request).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
    val ranked = response.flatMap(r =>
      decode[RankResponse](new String(r.entity.body.compile.toList.unsafeRunSync().toArray)).toOption
    )
    ranked.map(_.items.size) shouldBe Some(3)
  }

}
