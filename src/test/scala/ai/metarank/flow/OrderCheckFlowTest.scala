package ai.metarank.flow

import ai.metarank.model.Timestamp
import ai.metarank.util.{TestInteractionEvent, TestItemEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Random, Try}

class OrderCheckFlowTest extends AnyFlatSpec with Matchers {
  it should "validate sorted events" in {
    val events = (0 until 1000).map(i => TestItemEvent(i.toString).copy(timestamp = Timestamp(i)))
    val result = fs2.Stream
      .emits(events)
      .through(OrderCheckFlow.process)
      .compile
      .toList
      .unsafeRunSync()
    result shouldBe events
  }

  it should "fail on random ordering" in {
    val events = (0 until 1000).map(i => TestItemEvent(i.toString).copy(timestamp = Timestamp(Random.nextInt())))
    val result = Try(
      fs2.Stream
        .emits(events)
        .through(OrderCheckFlow.process)
        .compile
        .toList
        .unsafeRunSync()
    )
    result.isFailure shouldBe true

  }
}
