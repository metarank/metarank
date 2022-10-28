package ai.metarank.feature

import ai.metarank.feature.RateFeature.{NormalizeSchema, RateFeatureSchema}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scope.{GlobalScope, ItemScope}
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.model.{FeatureSchema, Key}
import ai.metarank.util.{TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class NormRateFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val conf = RateFeatureSchema(
    FeatureName("ctr"),
    "click",
    "impression",
    24.hours,
    List(7, 14),
    normalize = Some(NormalizeSchema(10))
  )
  val feature = RateFeature(conf)

  it should "decode schema" in {
    val in =
      """name: ctr
        |type: rate
        |top: click
        |bottom: impression
        |bucket: 24h
        |periods: [7,14]
        |normalize:
        |  weight: 10""".stripMargin
    parse(in).flatMap(_.as[FeatureSchema]) shouldBe Right(conf)
  }

  it should "extract writes" in {
    val click = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "click")
    feature.writes(click).unsafeRunSync().toList shouldBe List(
      PeriodicIncrement(Key(ItemScope(ItemId("p1")), FeatureName("ctr_click")), click.timestamp, 1),
      PeriodicIncrement(Key(GlobalScope, FeatureName("ctr_click_norm")), click.timestamp, 1)
    )
    val impression = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "impression")
    feature.writes(impression).unsafeRunSync().toList shouldBe List(
      PeriodicIncrement(Key(ItemScope(ItemId("p1")), FeatureName("ctr_impression")), impression.timestamp, 1),
      PeriodicIncrement(Key(GlobalScope, FeatureName("ctr_impression_norm")), impression.timestamp, 1)
    )
    val dummy = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "dummy")
    feature.writes(dummy).unsafeRunSync().toList shouldBe empty
  }

  it should "compute value" in {
    val p1 = List(
      TestInteractionEvent("p1", "x").copy(`type` = "impression"),
      TestInteractionEvent("p1", "x").copy(`type` = "impression"),
      TestInteractionEvent("p1", "x").copy(`type` = "impression"),
      TestInteractionEvent("p1", "x").copy(`type` = "click")
    )
    val p2 = List.concat(
      (0 until 90).map(_ => TestInteractionEvent("p2", "x").copy(`type` = "impression")),
      (0 until 9).map(_ => TestInteractionEvent("p2", "x").copy(`type` = "click"))
    )
    val values = process(
      events = List.concat(p1, p2),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(VectorValue(FeatureName("ctr"), Array(0.11458333333333333, 0.11458333333333333), 2)))
  }
}
