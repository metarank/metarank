package ai.metarank.validate

import ai.metarank.model.EventId
import ai.metarank.model.Identifier.ItemId
import ai.metarank.util.{TestConfig, TestInteractionEvent, TestRankingEvent}
import ai.metarank.validate.checks.InteractionPositionValidation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractionPositionValidationTest extends AnyFlatSpec with Matchers {
  it should "accept ok feeds" in {
    val result = InteractionPositionValidation.validate(
      TestConfig(),
      List(
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e1")),
        TestInteractionEvent("p1", "e1"),
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e2")),
        TestInteractionEvent("p2", "e2")
      )
    )
    result shouldBe empty
  }

  it should "fail on clicks on same position" in {
    val result = InteractionPositionValidation.validate(
      TestConfig(),
      List(
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e1")),
        TestInteractionEvent("p1", "e1"),
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e2")),
        TestInteractionEvent("p1", "e2")
      )
    )
    result shouldNot be(empty)
  }

  it should "fail on ghost items" in {
    val result = InteractionPositionValidation.validate(
      TestConfig(),
      List(
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e1")),
        TestInteractionEvent("p1", "e1"),
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e2")),
        TestInteractionEvent("p2", "e2"),
        TestRankingEvent(List("p1", "p2")).copy(id = EventId("e3")),
        TestInteractionEvent("p3", "e3")
      )
    )
    result shouldNot be(empty)
  }
}
