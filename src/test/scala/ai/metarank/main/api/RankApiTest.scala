package ai.metarank.main.api

import ai.metarank.api.routes.RankApi
import ai.metarank.api.routes.RankApi.RankResponse
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.ml.Ranker
import ai.metarank.util.{TestFeatureMapping, TestRankingEvent}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import org.http4s.{Entity, Method, Request, Response, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import io.circe.parser._
import scodec.bits.ByteVector

class RankApiTest extends AnyFlatSpec with Matchers {
  lazy val mapping = TestFeatureMapping()
  lazy val store   = MemPersistence(mapping.schema)
  lazy val service = RankApi(Ranker(mapping, store))

  it should "respond with the same data reranked" in {
    val response =
      service.ranker.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", explain = false).unsafeRunSync()
    response.items.map(_.item.value) shouldBe List("p1", "p2", "p3")
  }

  it should "emit feature values" in {
    val response =
      service.ranker.rerank(TestRankingEvent(List("p1", "p2", "p3")), "random", explain = true).unsafeRunSync()
    response.items.forall(_.features.map(_.size).contains(5)) shouldBe true
  }

  it should "accept ranking json event with explain" in {
    val response = post(
      uri = "http://localhost:8080/rank/random?explain=true",
      payload = TestRankingEvent.event(List("p1", "p2", "p3")).asJson.noSpaces
    )
    val br = 1
    response.map(_.items.size) shouldBe Right(3)
    response.map(_.state.isDefined) shouldBe Right(true)
    response.forall(_.items.forall(_.features.isEmpty)) shouldBe false
  }

  it should "accept ranking json event without explain" in {
    val response = post(
      uri = "http://localhost:8080/rank/random?explain=false",
      payload = TestRankingEvent.event(List("p1", "p2", "p3")).asJson.noSpaces
    )
    response.map(_.items.size) shouldBe Right(3)
    response.map(_.state.isDefined) shouldBe Right(false)
    response.forall(_.items.forall(_.features.isEmpty)) shouldBe true
  }

  def post(uri: String, payload: String): Either[Throwable, RankResponse] = {
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(uri),
      entity = Entity.strict(ByteVector(payload.getBytes))
    )
    val response = service.routes.apply(request).value.unsafeRunSync()
    response.map(r => new String(r.entity.body.compile.toList.unsafeRunSync().toArray)) match {
      case None       => Left(new Exception("empty response"))
      case Some(json) => decode[RankResponse](json)
    }
  }

}
