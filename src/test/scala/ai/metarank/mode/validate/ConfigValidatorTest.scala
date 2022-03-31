package ai.metarank.mode.validate

import ai.metarank.mode.validate.CheckResult.{FailedCheck, SuccessfulCheck}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigValidatorTest extends AnyFlatSpec with Matchers {
  it should "accept valid file" in {
    val yaml =
      """interactions:
        |  - name: click
        |    weight: 1
        |features:
        |  - name: foo
        |    type: number
        |    scope: item
        |    source: metadata.foo""".stripMargin
    ConfigValidator.check(yaml) shouldBe SuccessfulCheck
  }
  it should "fail on empty file" in {
    ConfigValidator.check("") shouldBe a[FailedCheck]
  }

  it should "fail on missing interactions" in {
    val yaml =
      """features:
        |  - name: foo
        |    type: number
        |    scope: item
        |    source: metadata.foo""".stripMargin
    ConfigValidator.check(yaml) shouldBe a[FailedCheck]
  }

  it should "fail on empty interactions" in {
    val yaml =
      """interactions:
        |features:
        |  - name: foo
        |    type: number
        |    scope: item
        |    source: metadata.foo""".stripMargin
    ConfigValidator.check(yaml) shouldBe a[FailedCheck]
  }

  it should "fail on interactions being non-object" in {
    val yaml =
      """interactions: true
        |features:
        |  - name: foo
        |    type: number
        |    scope: item
        |    source: metadata.foo""".stripMargin
    ConfigValidator.check(yaml) shouldBe a[FailedCheck]
  }

  it should "fail on missing features" in {
    val yaml =
      """interactions:
        |  - name: click
        |    weight: 1
        |    """.stripMargin
    ConfigValidator.check(yaml) shouldBe a[FailedCheck]
  }

  it should "fail on empty features" in {
    val yaml =
      """interactions:
        |  - name: click
        |    weight: 1
        |features:""".stripMargin
    ConfigValidator.check(yaml) shouldBe a[FailedCheck]
  }

  it should "fail on non-obj features" in {
    val yaml =
      """interactions:
        |  - name: click
        |    weight: 1
        |features: true
        |""".stripMargin
    ConfigValidator.check(yaml) shouldBe a[FailedCheck]
  }
}
