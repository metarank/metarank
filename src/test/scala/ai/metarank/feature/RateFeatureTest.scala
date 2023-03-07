package ai.metarank.feature

import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.{FeatureSchema, Key, Schema}
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scope.ItemScope
import ai.metarank.model.ScopeType.ItemScopeType
import ai.metarank.model.Write.PeriodicIncrement
import ai.metarank.util.{TestInteractionEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class RateFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val conf = RateFeatureSchema(
    FeatureName("ctr"),
    "click",
    "impression",
    ItemScopeType,
    24.hours,
    List(7, 14),
    refresh = Some(0.seconds)
  )
  val feature = RateFeature(conf)
  val store   = MemPersistence(Schema(feature.states))

  it should "decode schema" in {
    val in = "name: ctr\ntype: rate\ntop: click\nbottom: impression\nbucket: 24h\nperiods: [7,14]\nrefresh: 0s"
    parse(in).flatMap(_.as[FeatureSchema]) shouldBe Right(conf)
  }

  it should "fail decoding schema with user scope" in {
    val in =
      "name: ctr\ntype: rate\ntop: click\nbottom: impression\nbucket: 24h\nperiods: [7,14]\nscope: user\nrefresh: 0s"
    parse(in).flatMap(_.as[FeatureSchema]) shouldBe a[Left[_, _]]
  }

  it should "extract writes" in {
    val click = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "click")
    feature.writes(click, store).unsafeRunSync().toList shouldBe List(
      PeriodicIncrement(Key(ItemScope(ItemId("p1")), FeatureName("ctr_click")), click.timestamp, 1)
    )
    val impression = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "impression")
    feature.writes(impression, store).unsafeRunSync().toList shouldBe List(
      PeriodicIncrement(
        Key(ItemScope(ItemId("p1")), FeatureName("ctr_impression")),
        impression.timestamp,
        1
      )
    )
    val dummy = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "dummy")
    feature.writes(dummy, store).unsafeRunSync().toList shouldBe empty
  }

  it should "compute value" in {
    val values = process(
      events = List(
        TestInteractionEvent("p1", "x").copy(`type` = "impression"),
        TestInteractionEvent("p1", "x").copy(`type` = "impression"),
        TestInteractionEvent("p1", "x").copy(`type` = "impression"),
        TestInteractionEvent("p1", "x").copy(`type` = "impression"),
        TestInteractionEvent("p1", "x").copy(`type` = "click")
      ),
      feature.schema,
      TestRankingEvent(List("p1"))
    )
    values shouldBe List(List(VectorValue(FeatureName("ctr"), Array(0.25, 0.25), 2)))
  }
}
