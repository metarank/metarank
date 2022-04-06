package ai.metarank.feature

import ai.metarank.feature.NumberFeature.NumberFeatureSchema
import ai.metarank.feature.RateFeature.RateFeatureSchema
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.{FeatureSchema, FieldName, ItemId}
import ai.metarank.model.FieldName.Item
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.{TestInteractionEvent, TestRankingEvent}
import io.circe.yaml.parser.parse
import io.findify.featury.model.{Key, PeriodicCounterValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Scope, Tag, Tenant}
import io.findify.featury.model.PeriodicCounterValue.PeriodicValue
import io.findify.featury.model.Write.PeriodicIncrement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class RateFeatureTest extends AnyFlatSpec with Matchers {
  val conf    = RateFeatureSchema("ctr", "click", "impression", 24.hours, List(7, 14), ItemScope)
  val feature = RateFeature(conf)

  it should "decode schema" in {
    val in = "name: ctr\ntype: rate\nscope: item\ntop: click\nbottom: impression\nbucket: 24h\nperiods: [7,14]"
    parse(in).flatMap(_.as[FeatureSchema]) shouldBe Right(conf)
  }

  it should "extract writes" in {
    val click = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "click")
    feature.writes(click) shouldBe List(
      PeriodicIncrement(Key(Tag(Scope("item"), "p1"), FeatureName("ctr_click"), Tenant("default")), click.timestamp, 1)
    )
    val impression = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "impression")
    feature.writes(impression) shouldBe List(
      PeriodicIncrement(
        Key(Tag(Scope("item"), "p1"), FeatureName("ctr_impression"), Tenant("default")),
        impression.timestamp,
        1
      )
    )
    val dummy = TestInteractionEvent("p1", "i1", Nil).copy(`type` = "dummy")
    feature.writes(dummy) shouldBe empty
  }

  it should "compute value" in {
    val k1 = Key(Tag(Scope("item"), "p1"), FeatureName("ctr_click"), Tenant("default"))
    val k2 = Key(Tag(Scope("item"), "p1"), FeatureName("ctr_impression"), Tenant("default"))
    val state = Map(
      k1 -> PeriodicCounterValue(
        k1,
        Timestamp.now,
        List(PeriodicValue(Timestamp(0), Timestamp(0), 7, 10), PeriodicValue(Timestamp(0), Timestamp(0), 14, 100))
      ),
      k2 -> PeriodicCounterValue(
        k2,
        Timestamp.now,
        List(PeriodicValue(Timestamp(0), Timestamp(0), 7, 50), PeriodicValue(Timestamp(0), Timestamp(0), 14, 500))
      )
    )
    val result1 = feature.value(TestRankingEvent(List("p1", "p2")), state, ItemRelevancy(ItemId("p1")))
    result1.asInstanceOf[VectorValue].values.toList shouldBe List(0.2, 0.2)
    val result2 = feature.value(TestRankingEvent(List("p1", "p2")), state, ItemRelevancy(ItemId("p2")))
    result2.asInstanceOf[VectorValue].values.toList shouldBe List(0.0, 0.0)
  }

  it should "not skew dimensions if values are broken" in {
    val k1 = Key(Tag(Scope("item"), "p1"), FeatureName("ctr_click"), Tenant("default"))
    val k2 = Key(Tag(Scope("item"), "p1"), FeatureName("ctr_impression"), Tenant("default"))
    val state = Map(
      k1 -> PeriodicCounterValue(
        k1,
        Timestamp.now,
        List(PeriodicValue(Timestamp(0), Timestamp(0), 7, 10)) // must be two, but only one present
      ),
      k2 -> PeriodicCounterValue(
        k2,
        Timestamp.now,
        List(PeriodicValue(Timestamp(0), Timestamp(0), 7, 50))
      )
    )
    val result1 = feature.value(TestRankingEvent(List("p1", "p2")), state, ItemRelevancy(ItemId("p1")))
    result1.asInstanceOf[VectorValue].values.toList shouldBe List(0.0, 0.0)
  }
}
