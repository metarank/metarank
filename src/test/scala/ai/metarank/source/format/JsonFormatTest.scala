package ai.metarank.source.format

import ai.metarank.model.Event
import ai.metarank.util.TestInteractionEvent
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax._

class JsonFormatTest extends AnyFlatSpec with Matchers {
  val event = TestInteractionEvent("p1", "p2")
  val json  = event.asInstanceOf[Event].asJson.noSpaces

  it should "select json-line" in {
    val bytes   = (json + "\n" + json).getBytes()
    val decoded = fs2.Stream.emits(bytes).through(JsonFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe List(event, event)
  }

  it should "select json-array" in {
    val bytes   = ("[" + json + "," + json + "]").getBytes()
    val decoded = fs2.Stream.emits(bytes).through(JsonFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe List(event, event)
  }

  it should "handle empty" in {
    val decoded = fs2.Stream.emits(Array.emptyByteArray).through(JsonFormat.parse).compile.toList.unsafeRunSync()
    decoded shouldBe Nil
  }
}
