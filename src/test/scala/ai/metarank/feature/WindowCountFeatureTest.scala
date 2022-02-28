package ai.metarank.feature

import ai.metarank.feature.WindowCountFeature.WindowCountSchema
import ai.metarank.model.FeatureScope.ItemScope
import ai.metarank.model.ItemId
import ai.metarank.model.MValue.VectorValue
import ai.metarank.util.{TestInteractionEvent, TestMetadataEvent, TestRankingEvent}
import io.findify.featury.model.{Key, PeriodicCounterState, PeriodicCounterValue, Timestamp}
import io.findify.featury.model.Key.{FeatureName, Tag, Tenant}
import io.findify.featury.model.PeriodicCounterState.TimeCounter
import io.findify.featury.model.PeriodicCounterValue.PeriodicValue
import io.findify.featury.model.Write.{Increment, PeriodicIncrement}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class WindowCountFeatureTest extends AnyFlatSpec with Matchers {
  val feature = WindowCountFeature(
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
    val write = feature.writes(event)
    write shouldBe List(
      PeriodicIncrement(Key(Tag(ItemScope.scope, "p1"), FeatureName("cnt"), Tenant("default")), event.timestamp, 1)
    )
  }

  it should "ignore non-interaction events" in {
    feature.writes(TestMetadataEvent("p1")) shouldBe Nil
    feature.writes(TestRankingEvent(List("p1"))) shouldBe Nil
  }

  it should "compute values" in {
    val key = Key(Tag(ItemScope.scope, "p1"), FeatureName("cnt"), Tenant("default"))
    val now = Timestamp.now
    val value = feature.value(
      request = TestRankingEvent(List("p1")),
      state = Map(key -> PeriodicCounterValue(key, now, List(PeriodicValue(now.minus(24.hours), now, 1, 1)))),
      id = ItemId("p1")
    )
    value should matchPattern {
      case VectorValue("cnt_1" :: Nil, values, 1) if values.toList == List(1.0) =>
    }
  }
}
