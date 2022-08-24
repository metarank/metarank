package ai.metarank.source.format

import ai.metarank.model.Event
import ai.metarank.util.TestInteractionEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

import scala.util.Try

class JsonArrayFormatTest extends AnyFlatSpec with Matchers {
  val event = TestInteractionEvent("p1", "p2")
  val json  = event.asInstanceOf[Event].asJson.noSpaces

  it should "decode events" in {
    val bytes   = ("[" + json + "," + json + "]").getBytes()
    val decoded = fs2.Stream.emits(bytes).through(JsonFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe List(event, event)
  }

  it should "decode empty arrays" in {
    val bytes   = ("[]").getBytes()
    val decoded = fs2.Stream.emits(bytes).through(JsonFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe Nil
  }

  it should "fail on non-json" in {
    val decoded = Try(fs2.Stream.emits("YOLO".getBytes()).through(JsonFormat.parse).compile.toList.unsafeRunSync())
    decoded.isFailure shouldBe true
  }

}
