package ai.metarank.main.api

import ai.metarank.model.Event
import ai.metarank.util.TestRankingEvent
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import io.circe.Encoder
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._
import org.http4s.{Entity, Method, Request, Uri}

class FeedbackApiTest extends AnyFlatSpec with Matchers {
  val queue   = Queue.unbounded[IO, Option[Event]].unsafeRunSync()
  val service = FeedbackApi(queue)

  it should "accept feedback events in json format" in {
    val event = TestRankingEvent.event(List("p1")).asJson.noSpaces
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/feedback"),
      entity = Entity.strict(Chunk.array(event.getBytes()))
    )

    val response = service.routes(request).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
    queue.take.unsafeRunSync().isDefined shouldBe true
  }
  it should "accept feedback events in json-array format" in {
    val event =
      Encoder
        .encodeList[Event]
        .apply(List(TestRankingEvent.event(List("p1")), TestRankingEvent.event(List("p1"))))
        .noSpaces
    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString("http://localhost:8080/feedback"),
      entity = Entity.strict(Chunk.array(event.getBytes()))
    )

    val response = service.routes(request).value.unsafeRunSync()
    response.map(_.status.code) shouldBe Some(200)
    queue.take.unsafeRunSync().isDefined shouldBe true
  }
}
