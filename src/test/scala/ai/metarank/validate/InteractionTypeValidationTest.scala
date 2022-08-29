package ai.metarank.validate

import ai.metarank.config.ModelConfig.LambdaMARTConfig
import ai.metarank.config.ModelConfig.ModelBackend.XGBoostBackend
import ai.metarank.model.Key.FeatureName
import ai.metarank.util.{TestConfig, TestInteractionEvent}
import ai.metarank.validate.checks.InteractionTypeValidation
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractionTypeValidationTest extends AnyFlatSpec with Matchers {
  val conf = TestConfig().copy(models =
    Map("fo" -> LambdaMARTConfig(XGBoostBackend(), NonEmptyList.of(FeatureName("price")), Map("click" -> 1)))
  )

  it should "accept correct types" in {
    val result = InteractionTypeValidation.validate(
      config = conf,
      events = List(TestInteractionEvent("p1", "e1").copy(`type` = "click"))
    )
    result shouldBe empty
  }

  it should "fail on unknown event type" in {
    val result = InteractionTypeValidation.validate(
      config = conf,
      events = List(TestInteractionEvent("p1", "e1").copy(`type` = "prrr"))
    )
    result shouldNot be(empty)
  }
}
