package ai.metarank.validate

import ai.metarank.util.{TestConfig, TestInteractionEvent, TestItemEvent, TestRankingEvent}
import ai.metarank.validate.checks.EventTypesValidation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class EventTypesValidationTest extends AnyFlatSpec with Matchers {
  it should "accept proper feeds" in {
    val result = EventTypesValidation.validate(
      TestConfig(),
      List(TestItemEvent("p1"), TestRankingEvent(List("p1")), TestInteractionEvent("p1", "e"))
    )
    result shouldBe empty
  }

  it should "fail on missing item" in {
    val result = EventTypesValidation.validate(
      TestConfig(),
      List(TestRankingEvent(List("p1")), TestInteractionEvent("p1", "e"))
    )
    result shouldNot be(empty)
  }

  it should "fail on missing ranking" in {
    val result = EventTypesValidation.validate(
      TestConfig(),
      List(TestItemEvent("p1"), TestInteractionEvent("p1", "e"))
    )
    result shouldNot be(empty)
  }

  it should "fail on missing interaction" in {
    val result = EventTypesValidation.validate(
      TestConfig(),
      List(TestItemEvent("p1"), TestRankingEvent(List("p1")))
    )
    result shouldNot be(empty)
  }
}
