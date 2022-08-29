package ai.metarank.validate

import ai.metarank.model.Timestamp
import ai.metarank.util.{TestConfig, TestItemEvent}
import ai.metarank.validate.checks.EventOrderValidation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class EventOrderValidationTest extends AnyFlatSpec with Matchers {
  lazy val now = Timestamp.now
  it should "accept correct events" in {
    val result = EventOrderValidation.validate(
      TestConfig(),
      List(
        TestItemEvent("p1").copy(timestamp = now),
        TestItemEvent("p1").copy(timestamp = now.plus(1.second)),
        TestItemEvent("p1").copy(timestamp = now.plus(2.second))
      )
    )
    result shouldBe Nil
  }

  it should "fail on wrong ordering" in {
    val result = EventOrderValidation.validate(
      TestConfig(),
      List(
        TestItemEvent("p1").copy(timestamp = now),
        TestItemEvent("p1").copy(timestamp = now.plus(1.second)),
        TestItemEvent("p1").copy(timestamp = now.minus(2.second))
      )
    )
    result.nonEmpty shouldBe true
  }
}
