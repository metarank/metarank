package ai.metarank.source

import ai.metarank.model.Event
import ai.metarank.util.TestItemEvent
import cats.effect.{ExitCode, IO, Ref}
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class RestApiEventSourceTest extends AnyFlatSpec with Matchers {
  val queue = Queue.bounded[IO, Option[Event]](100).unsafeRunSync()
  val host  = "localhost"
  val port  = 1024 + Random.nextInt(50000)

  it should "receive event" in {
    val event = TestItemEvent("p1")
    queue.offer(Some(event)).unsafeRunSync()
    queue.offer(None).unsafeRunSync()
    val results = RestApiEventSource(queue).stream.compile.toList.unsafeRunSync()
    results shouldBe List(event)
  }
}
