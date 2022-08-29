package ai.metarank.validate

import ai.metarank.model.{EventId, Timestamp}
import ai.metarank.util.{TestConfig, TestInteractionEvent, TestItemEvent, TestRankingEvent}
import ai.metarank.validate.checks.InteractionMetadataValidation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class InteractionMetadataValidationTest extends AnyFlatSpec with Matchers {
  val now = Timestamp.now
  it should "accept item+int" in {
    val result = InteractionMetadataValidation.validate(
      TestConfig(),
      List(
        TestItemEvent("p1").copy(timestamp = now),
        TestInteractionEvent("p1", "e1").copy(timestamp = now.plus(1.second))
      )
    )
    result.isEmpty shouldBe true
  }

  it should "fail on missing item" in {
    val result = InteractionMetadataValidation.validate(TestConfig(), List(TestInteractionEvent("p1", "e1")))
    result.isEmpty shouldBe false
  }

  it should "fail on out-of-order interaction" in {
    val result = InteractionMetadataValidation.validate(
      TestConfig(),
      List(
        TestInteractionEvent("p1", "e1").copy(timestamp = now),
        TestItemEvent("p1").copy(timestamp = now.plus(10.seconds))
      )
    )
    result.isEmpty shouldBe false
  }
}
