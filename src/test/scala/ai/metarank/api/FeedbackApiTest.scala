package ai.metarank.api

import ai.metarank.mode.standalone.api.FeedbackApi
import ai.metarank.model.Event
import ai.metarank.util.TestInteractionEvent
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import org.http4s.{Request, Uri}
import org.http4s.dsl.io.{GET, NoContent, Ok}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FeedbackApiTest extends AnyFlatSpec with Matchers {

  it should "return no-content" in {
    val queue    = Queue.dropping[IO, Event](100).unsafeRunSync()
    val feedback = FeedbackApi(queue).routes.orNotFound
    val response =
      feedback.run(Request[IO](uri = Uri.unsafeFromString("http://localhost:8080/feedback"))).unsafeRunSync()
    response.status shouldBe NoContent
  }

  it should "return item" in {
    val queue    = Queue.dropping[IO, Event](100).unsafeRunSync()
    val feedback = FeedbackApi(queue).routes.orNotFound
    queue.offer(TestInteractionEvent("p1", "p0")).unsafeRunSync()
    val response =
      feedback.run(Request[IO](uri = Uri.unsafeFromString("http://localhost:8080/feedback"))).unsafeRunSync()
    response.status shouldBe Ok
  }
}
