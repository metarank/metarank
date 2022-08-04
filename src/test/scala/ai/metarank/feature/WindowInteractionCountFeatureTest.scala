package ai.metarank.feature

import ai.metarank.feature.WindowInteractionCountFeature.WindowCountSchema
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.ScopeType.ItemScope
import ai.metarank.model.Identifier.ItemId
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.persistence.field.MapFieldStore
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import io.findify.featury.model.{Key, PeriodicCounterState, PeriodicCounterValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Tag, Tenant}
import io.findify.featury.model.PeriodicCounterValue.PeriodicValue
import io.findify.featury.model.Write.{Increment, PeriodicIncrement}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class WindowInteractionCountFeatureTest extends AnyFlatSpec with Matchers {
  val feature = WindowInteractionCountFeature(
    WindowCountSchema(
      name = "cnt",
      interaction = "click",
      bucket = 24.hours,
      periods = List(1),
      scope = ItemScope
    )
  )

  it should "count item clicks" in {
    val event = TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"))
    val write = feature.writes(event, MapFieldStore()).toList
    write shouldBe List(
      PeriodicIncrement(Key(Tag(ItemScope.scope, "p1"), FeatureName("cnt"), Tenant("default")), event.timestamp, 1)
    )
  }

  it should "ignore non-interaction events" in {
    feature.writes(TestItemEvent("p1"), MapFieldStore()) shouldBe Nil
    feature.writes(TestRankingEvent(List("p1")), MapFieldStore()) shouldBe Nil
  }

  it should "compute values" in {
    val key = Key(Tag(ItemScope.scope, "p1"), FeatureName("cnt"), Tenant("default"))
    val now = Timestamp.now
    val value = feature.value(
      request = TestRankingEvent(List("p1")),
      features = Map(key -> PeriodicCounterValue(key, now, List(PeriodicValue(now.minus(24.hours), now, 1, 1)))),
      id = ItemRelevancy(ItemId("p1"))
    )
    value should matchPattern {
      case VectorValue("cnt_1" :: Nil, values, 1) if values.toList == List(1.0) =>
    }
  }
}
