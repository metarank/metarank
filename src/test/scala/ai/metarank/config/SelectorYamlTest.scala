package ai.metarank.config

import ai.metarank.config.ModelConfig.NoopConfig
import ai.metarank.config.Selector.{AcceptSelector, AndSelector, FieldSelector, NotSelector, SampleSelector}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.yaml.parser.parse

class SelectorYamlTest extends AnyFlatSpec with Matchers {
  it should "fall back to all when no selector field" in {
    val yaml   = "type: noop"
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = AcceptSelector()))
  }

  it should "load explicit field selector" in {
    val yaml =
      """type: noop
        |selector:
        |  rankingField: foo
        |  value: bar""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = FieldSelector("foo", "bar")))
  }

  it should "load explicit accept selector" in {
    val yaml =
      """type: noop
        |selector:
        |  accept: true""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = AcceptSelector()))
  }

  it should "load explicit not selector" in {
    val yaml =
      """type: noop
        |selector:
        |  not:
        |    accept: true""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = NotSelector(AcceptSelector())))
  }

  it should "load explicit sample selector" in {
    val yaml =
      """type: noop
        |selector:
        |  ratio: 0.5""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = SampleSelector(0.5)))
  }

  it should "load explicit and selector" in {
    val yaml =
      """type: noop
        |selector:
        |  and:
        |    - ratio: 0.5
        |    - rankingField: foo
        |      value: bar""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = AndSelector(List(SampleSelector(0.5), FieldSelector("foo", "bar")))))
  }

}
