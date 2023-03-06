package ai.metarank.feature

import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.fstore.memory.MemPersistence
import ai.metarank.model.Field.StringField
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.{FeatureKey, FeatureSchema, Key, Schema}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.VectorValue
import ai.metarank.model.Scalar.SString
import ai.metarank.model.Scope.{ItemFieldScope, ItemScope}
import ai.metarank.model.ScopeType.{ItemFieldScopeType, ItemScopeType}
import ai.metarank.model.Write.{PeriodicIncrement, Put}
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import io.circe.yaml.parser.parse

import scala.concurrent.duration._

class ScopedRateFeatureTest extends AnyFlatSpec with Matchers with FeatureTest {
  val conf =
    RateFeatureSchema(FeatureName("ctr"), "click", "impression", ItemFieldScopeType("color"), 24.hours, List(7, 14))
  val feature = RateFeature(conf)
  val store   = MemPersistence(Schema(feature.states))
  val item    = TestItemEvent("p1", List(StringField("color", "red")))

  it should "decode schema" in {
    val in = "name: ctr\ntype: rate\ntop: click\nbottom: impression\nbucket: 24h\nperiods: [7,14]\nscope: item.color"
    parse(in).flatMap(_.as[FeatureSchema]) shouldBe Right(conf)
  }

  it should "extract item writes when field matches" in {
    val w1 = feature.writes(item, store).unsafeRunSync()
    w1 shouldBe List(Put(Key(ItemScope(ItemId("p1")), feature.fieldScope.name), item.timestamp, SString("red")))
  }

  it should "drop item writes on field mismatch" in {
    val item = TestItemEvent("p1", List(StringField("size", "xxl")))
    val w1   = feature.writes(item, store).unsafeRunSync()
    w1 shouldBe Nil
  }

  it should "extract inctements" in {
    store.scalars.get(FeatureKey(ItemScopeType, FeatureName("ctr_field"))) match {
      case Some(value) =>
        value.put(Put(Key(ItemScope(ItemId("p1")), FeatureName("ctr_field")), item.timestamp, SString("red"))).unsafeRunSync()
      case None => ???
    }
    val click = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "click")
    feature.writes(click, store).unsafeRunSync().toList shouldBe List(
      PeriodicIncrement(Key(ItemFieldScope(ItemId("p1"), "color", "red"), FeatureName("ctr_click")), click.timestamp, 1)
    )
    val impression = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "impression")
    feature.writes(impression, store).unsafeRunSync().toList shouldBe List(
      PeriodicIncrement(
        Key(ItemFieldScope(ItemId("p1"), "color", "red"), FeatureName("ctr_impression")),
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
