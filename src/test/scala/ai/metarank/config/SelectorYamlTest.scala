package ai.metarank.config

import ai.metarank.config.Selector.{
  AcceptSelector,
  AndSelector,
  CadenceSelector,
  FieldSelector,
  InteractionPositionSelector,
  NotSelector,
  RankingLengthSelector,
  SampleSelector,
  UserSelector
}
import ai.metarank.ml.rank.NoopRanker.NoopConfig
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

  it should "load max-position selector" in {
    val yaml =
      """type: noop
        |selector:
        |  maxInteractionPosition: 10""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(
      NoopConfig(selector =
        InteractionPositionSelector(maxInteractionPosition = Some(10), minInteractionPosition = None)
      )
    )
  }

  it should "load ranking-length selector" in {
    val yaml =
      """type: noop
        |selector:
        |  minItems: 10""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(
      NoopConfig(selector = RankingLengthSelector(minItems = Some(10), maxItems = None))
    )
  }

  it should "load user selector" in {
    val yaml =
      """type: noop
        |selector:
        |  user: monitoring-bot""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = UserSelector("monitoring-bot")))
  }

  it should "load cadence selector" in {
    val yaml =
      """type: noop
        |selector:
        |  periodSeconds: 300
        |  secondFrom: 90
        |  secondTo: 110""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(NoopConfig(selector = CadenceSelector(300, 90, 110)))
  }

  it should "reject a cadence selector with a slot outside the period" in {
    val yaml =
      """type: noop
        |selector:
        |  periodSeconds: 300
        |  secondFrom: 90
        |  secondTo: 400""".stripMargin
    parse(yaml).flatMap(_.as[ModelConfig]) shouldBe Symbol("left")
  }

  it should "load a combined synthetic-traffic exclusion selector" in {
    val yaml =
      """type: noop
        |selector:
        |  not:
        |    and:
        |      - minItems: 2
        |        maxItems: 2
        |      - periodSeconds: 300
        |        secondFrom: 90
        |        secondTo: 110""".stripMargin
    val result = parse(yaml).flatMap(_.as[ModelConfig])
    result shouldBe Right(
      NoopConfig(selector =
        NotSelector(
          AndSelector(List(RankingLengthSelector(Some(2), Some(2)), CadenceSelector(300, 90, 110)))
        )
      )
    )
  }

}
