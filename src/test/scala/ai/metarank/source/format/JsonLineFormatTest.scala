package ai.metarank.source.format

import ai.metarank.model.Event
import ai.metarank.util.TestInteractionEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

import scala.util.Try

class JsonLineFormatTest extends AnyFlatSpec with Matchers {
  val event = TestInteractionEvent("p1", "p2")
  val json  = event.asInstanceOf[Event].asJson.noSpaces

  it should "parse test events from bytes with no newline at end" in {
    val bytes   = (json + "\n" + json).getBytes()
    val decoded = fs2.Stream.emits(bytes).through(JsonLineFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe List(event, event)
  }

  it should "parse test events from bytes with newline at end" in {
    val bytes   = (json + "\n" + json + "\n").getBytes()
    val decoded = fs2.Stream.emits(bytes).through(JsonLineFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe List(event, event)
  }

  it should "parse empty file" in {
    val bytes   = Array.emptyByteArray
    val decoded = fs2.Stream.emits(bytes).through(JsonLineFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe Nil
  }

  it should "fail on non-json" in {
    val decoded = Try(fs2.Stream.emits("YOLO".getBytes()).through(JsonLineFormat.parse).compile.toList.unsafeRunSync())
    decoded.isFailure shouldBe true
  }
}
