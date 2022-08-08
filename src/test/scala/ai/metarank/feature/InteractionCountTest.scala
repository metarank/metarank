package ai.metarank.feature

import ai.metarank.feature.InteractionCountFeature.InteractionCountSchema
import ai.metarank.fstore.Persistence
import ai.metarank.model.Identifier.{ItemId, SessionId}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.{Env, Key}
import ai.metarank.model.MValue.SingleValue
import ai.metarank.model.Scope.{ItemScope, SessionScope}
import ai.metarank.model.ScopeType.{ItemScopeType, SessionScopeType}
import ai.metarank.model.Write.Increment
import ai.metarank.util.{TestInteractionEvent, TestItemEvent, TestRankingEvent}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InteractionCountTest extends AnyFlatSpec with Matchers with FeatureTest {
  val feature = InteractionCountFeature(
    InteractionCountSchema(
      name = FeatureName("cnt"),
      interaction = "click",
      scope = ItemScopeType
    )
  )

  it should "emit item increments on type match" in {
    val event = TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"))
    val write = feature.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    write shouldBe List(
      Increment(Key(ItemScope(Env("default"), ItemId("p1")), FeatureName("cnt")), event.timestamp, 1)
    )
  }

  it should "ignore non-interaction events" in {
    feature.writes(TestItemEvent("p1"), Persistence.blackhole()).unsafeRunSync() shouldBe Nil
    feature.writes(TestRankingEvent(List("p1")), Persistence.blackhole()).unsafeRunSync() shouldBe Nil
  }

  it should "also increment on session key" in {
    val sf = InteractionCountFeature(feature.schema.copy(scope = SessionScopeType))
    val event =
      TestInteractionEvent("e1", "e0").copy(`type` = "click", item = ItemId("p1"), session = Some(SessionId("s1")))
    val write = sf.writes(event, Persistence.blackhole()).unsafeRunSync().toList
    write shouldBe List(
      Increment(Key(SessionScope(Env("default"), SessionId("s1")), FeatureName("cnt")), event.timestamp, 1)
    )
  }

  it should "count events" in {
    val result = process(
      events = List(TestInteractionEvent("p1", "x"), TestInteractionEvent("p1", "x"), TestInteractionEvent("p1", "x")),
      schema = feature.schema,
      request = TestRankingEvent(List("p1"))
    )
    result shouldBe List(List(SingleValue("cnt", 3)))
  }
}
