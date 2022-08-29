package ai.metarank.validate

import ai.metarank.model.Event.RankingEvent
import ai.metarank.model.EventId
import ai.metarank.util.{TestConfig, TestInteractionEvent, TestRankingEvent}
import ai.metarank.validate.checks.InteractionKeyValidation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractionKeyValidationTest extends AnyFlatSpec with Matchers {
  it should "accept proper ids" in {
    val result = InteractionKeyValidation.validate(
      TestConfig(),
      List(
        TestRankingEvent(List("p1")).copy(id = EventId("e1")),
        TestInteractionEvent("p1", "e1")
      )
    )
    result shouldBe empty
  }

  it should "fail on key mismatch" in {
    val result = InteractionKeyValidation.validate(
      TestConfig(),
      List(
        TestRankingEvent(List("p1")).copy(id = EventId("e1")),
        TestInteractionEvent("p1", "e2")
      )
    )
    result shouldNot be(empty)
  }
}
