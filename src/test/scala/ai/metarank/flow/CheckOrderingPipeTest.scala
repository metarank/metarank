package ai.metarank.flow

import ai.metarank.model.Timestamp
import ai.metarank.util.TestItemEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import fs2.Stream

import scala.util.Try

class CheckOrderingPipeTest extends AnyFlatSpec with Matchers {
  val now = Timestamp.now

  it should "not fail on proper order" in {
    val events = List(
      TestItemEvent("p1").copy(timestamp = now.plus(1.second)),
      TestItemEvent("p1").copy(timestamp = now.plus(2.second)),
      TestItemEvent("p1").copy(timestamp = now.plus(3.second))
    )
    val result = Stream.emits(events).through(CheckOrderingPipe.process).compile.toList.unsafeRunSync()
    result shouldNot be(empty)
  }

  it should "fail in a case of ordering mismatch" in {
    val events = List(
      TestItemEvent("p1").copy(timestamp = now.plus(1.second)),
      TestItemEvent("p1").copy(timestamp = now.plus(2.second)),
      TestItemEvent("p1").copy(timestamp = now.plus(3.second)),
      TestItemEvent("p1").copy(timestamp = now.minus(3.second))
    )
    val result = Try(Stream.emits(events).through(CheckOrderingPipe.process).compile.toList.unsafeRunSync())
    result.isFailure shouldBe true
  }
}
