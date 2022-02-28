package ai.metarank.feature

import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope}
import ai.metarank.model.{ItemId, SessionId}
import ai.metarank.util.{TestInteractionEvent, TestMetadataEvent, TestRankingEvent}
import io.findify.featury.model.Key
import io.findify.featury.model.Key.{FeatureName, Tag, Tenant}
import io.findify.featury.model.Write.Increment
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractionCountTest extends AnyFlatSpec with Matchers {
  val feature = InteractionCountFeature(
    InteractionCountSchema(
      name = "cnt",
      interaction = "click",
      scope = ItemScope
    )
  )

  it should "emit item increments on type match" in {
    val event = TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"))
    val write = feature.writes(event)
    write shouldBe List(
      Increment(Key(Tag(ItemScope.scope, "p1"), FeatureName("cnt"), Tenant("default")), event.timestamp, 1)
    )
  }

  it should "ignore non-interaction events" in {
    feature.writes(TestMetadataEvent("p1")) shouldBe Nil
    feature.writes(TestRankingEvent(List("p1"))) shouldBe Nil
  }

  it should "also increment on session key" in {
    val sf    = InteractionCountFeature(feature.schema.copy(scope = SessionScope))
    val event = TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"), session = SessionId("s1"))
    val write = sf.writes(event)
    write shouldBe List(
      Increment(Key(Tag(SessionScope.scope, "s1"), FeatureName("cnt"), Tenant("default")), event.timestamp, 1)
    )
  }
}
