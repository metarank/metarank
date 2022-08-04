package ai.metarank.feature

import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.model.ScopeType.{ItemScope, SessionScope}
import ai.metarank.model.Identifier.{ItemId, SessionId}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.util.persistence.field.MapFieldStore
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import io.findify.featury.model.Key
import io.findify.featury.model.Key.{FeatureName, Tag, Tenant}
import io.findify.featury.model.Write.Increment
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractionCountTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = InteractionCountFeature(
    InteractionCountSchema(
      name = "cnt",
      interaction = "click",
      scope = ItemScope
    )
  )

  it should "emit item increments on type match" in {
    val event = TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"))
    val write = feature.writes(event, MapFieldStore()).toList
    write shouldBe List(
      Increment(Key(Tag(ItemScope.scope, "p1"), FeatureName("cnt"), Tenant("default")), event.timestamp, 1)
    )
  }

  it should "ignore non-interaction events" in {
    feature.writes(TestItemEvent("p1"), MapFieldStore()) shouldBe Nil
    feature.writes(TestRankingEvent(List("p1")), MapFieldStore()) shouldBe Nil
  }

  it should "also increment on session key" in {
    val sf = InteractionCountFeature(feature.schema.copy(scope = SessionScope))
    val event =
      TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"), session = Some(SessionId("s1")))
    val write = sf.writes(event, MapFieldStore()).toList
    write shouldBe List(
      Increment(Key(Tag(SessionScope.scope, "s1"), FeatureName("cnt"), Tenant("default")), event.timestamp, 1)
    )
  }

  it should "count events" in {
    val result = process(
      events = List(TestInteractionEvent("p1", "x"), TestInteractionEvent("p1", "x"), TestInteractionEvent("p1", "x")),
      schema = feature.schema,
      request = TestRankingEvent(List("p1"))
    )
    result should matchPattern { case List(List(SingleValue("cnt", 3))) =>
    }
  }
}
